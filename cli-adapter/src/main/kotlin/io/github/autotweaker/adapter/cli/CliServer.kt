/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.adapter.cli

import com.google.auto.service.AutoService
import io.github.autotweaker.api.*
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.trace.recoverException
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.deleteIfExists

object CliServer : Loggable, Settable, Traceable {
	val isRunning get() = ::channel.isInitialized && channel.isOpen
	
	@AutoService(SettingDef::class)
	class MaxLineLength : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(10_485_760)
		override val description = "CLI接收消息的最大行长度（字节），超出会断开连接，默认10_485_760即10MB"
	}
	
	private val maxLineLength = setting.get(MaxLineLength()).value
	private val json = Json { ignoreUnknownKeys = true }
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val activeClients = ConcurrentHashMap.newKeySet<SocketChannel>()
	private val connectionLimit = Semaphore(64)
	private lateinit var channel: ServerSocketChannel
	private const val MAX_RESPONSE_CHUNK = 256 * 1024
	
	private val socketPath: Path = CONFIG_PATH.resolve("cli.sock")
	
	fun start(router: CommandRouter) {
		val path = socketPath
		Files.createDirectories(path.parent)
		path.deleteIfExists()
		
		channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
			bind(UnixDomainSocketAddress.of(path))
		}
		Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
		log.info("Started CliServer  socketPath={}", path)
		
		scope.launch {
			while (channel.isOpen) {
				val client = trace.catching { channel.accept() }
					.onFailure {
						log.warn(
							"Failed connection acceptance  socketPath={}  reason={}",
							socketPath,
							it.message
						)
					}
					.getOrNull() ?: break
				log.info("Client connected  socketPath={}", socketPath)
				connectionLimit.acquire()
				activeClients.add(client)
				scope.launch {
					trace.catching {
						handle(client, router)
					}.also {
						activeClients.remove(client)
						connectionLimit.release()
					}.getOrThrow()
				}
			}
		}
	}
	
	fun stop() {
		activeClients.forEach { trace.catching { it.close() } }
		activeClients.clear()
		trace.catching { channel.close() }
		scope.cancel()
		trace.catching { socketPath.deleteIfExists() }
		log.info("Stopped CliServer  socketPath={}", socketPath)
	}
	
	private suspend fun handle(client: SocketChannel, router: CommandRouter) {
		client.use {
			val line = readLine(client) ?: run {
				log.debug("Client sent no data")
				return
			}
			val command = (json.decodeFromString<CliMessage>(line) as? CliMessage.Command) ?: run {
				log.warn("Failed CLI message parsing")
				return
			}
			log.debug("Received CliMessage  command={}  argCount={}", command.command(), command.args.size)
			
			val prompt: suspend (text: String, echo: Boolean) -> String = { text, echo ->
				write(client, json.encodeToString<CliResponse>(CliResponse.Prompt(text, echo)))
				val line = readLine(client) ?: throw CancellationException("Client disconnected")
				val reply = json.decodeFromString<CliMessage>(line)
				(reply as? CliMessage.PromptResponse)?.text
					?: throw IllegalStateException("Expected PromptResponse, got ${reply::class.simpleName}")
			}
			
			var sawDone = false
			val cmdName = command.command()
			trace.catching {
				router.dispatch(command, prompt).collect { chunk ->
					when (chunk) {
						is CmdOutput.Data -> {
							val channel = chunk.channel.name.lowercase()
							val text = chunk.text
							if (text.length <= MAX_RESPONSE_CHUNK) {
								write(
									client,
									json.encodeToString<CliResponse>(CliResponse.Data(text, channel, chunk.newline))
								)
							} else {
								var offset = 0
								while (offset < text.length) {
									val end = minOf(offset + MAX_RESPONSE_CHUNK, text.length)
									write(
										client,
										json.encodeToString<CliResponse>(
											CliResponse.Data(
												text.substring(offset, end),
												channel,
												chunk.newline
											)
										)
									)
									offset = end
								}
							}
						}
						
						is CmdOutput.Done -> {
							sawDone = true
							write(client, json.encodeToString<CliResponse>(CliResponse.Done(chunk.exitCode)))
							return@collect
						}
					}
				}
			}.rethrowCancellation()
				.recoverException { e: IOException ->
					log.warn(
						"Disconnected client during command  command={}  reason={}",
						cmdName, e.message
					)
				}
				.onFailure { e ->
					log.error("Failed command  command={}", cmdName, e)
					trace.catching {
						write(
							client, json.encodeToString<CliResponse>(
								CliResponse.Data(e.message ?: "Internal error", "stderr", true)
							)
						)
					}
				}
			
			if (!sawDone) {
				log.warn("Command did not emit Done  command={}", cmdName)
				trace.catching {
					write(client, json.encodeToString<CliResponse>(CliResponse.Done(1)))
				}
			}
		}
	}
	
	private fun readLine(channel: SocketChannel): String? {
		val buf = ByteBuffer.allocate(4096)
		var remainder = ByteArray(0)
		val sb = StringBuilder()
		while (true) {
			buf.clear()
			if (remainder.isNotEmpty()) buf.put(remainder)
			val n = channel.read(buf)
			if (n == -1) return sb.toString().ifEmpty { null }
			buf.flip()
			val bytes = ByteArray(buf.remaining())
			buf.get(bytes)
			val validEnd = findValidUtf8End(bytes)
			remainder = bytes.copyOfRange(validEnd, bytes.size)
			val chunk = String(bytes, 0, validEnd, StandardCharsets.UTF_8)
			val nl = chunk.indexOf('\n')
			if (nl >= 0) {
				sb.append(chunk, 0, nl)
				return sb.toString()
			}
			if (sb.length + chunk.length > maxLineLength) {
				log.warn("Exceeded line max length  length={}", sb.length)
				return null
			}
			sb.append(chunk)
		}
	}
	
	private fun findValidUtf8End(bytes: ByteArray): Int {
		var i = bytes.size
		while (i > 0) {
			val b = bytes[i - 1].toInt() and 0xFF
			if (b < 0x80) break
			if (b and 0xC0 == 0xC0) {
				i--; break
			}
			i--
		}
		return i
	}
	
	private fun write(channel: SocketChannel, text: String) {
		if (text.isEmpty()) return
		val bytes = (text + "\n").toByteArray(StandardCharsets.UTF_8)
		var pos = 0
		while (pos < bytes.size) {
			val written = channel.write(ByteBuffer.wrap(bytes, pos, bytes.size - pos))
			if (written < 0) throw IOException("Channel not writable")
			pos += written
		}
	}
}
