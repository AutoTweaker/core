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
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
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

class CliServer(service: SettingService, core: CoreAPI) {
	private val trace = core.trace(this::class)
	
	@AutoService(SettingDef::class)
	class MaxLineLength : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(10_485_760)
		override val description = "CLI接收消息的最大行长度（字节），超出会断开连接，默认10_485_760即10MB"
	}
	
	private val maxLineLength = service.get(MaxLineLength()).value
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val json = Json { ignoreUnknownKeys = true }
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val activeClients = ConcurrentHashMap.newKeySet<SocketChannel>()
	private val connectionLimit = Semaphore(64)
	private lateinit var channel: ServerSocketChannel
	
	fun start(router: CommandRouter) {
		val path = socketPath()
		Files.createDirectories(path.parent)
		path.deleteIfExists()
		
		channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
			bind(UnixDomainSocketAddress.of(path))
		}
		Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
		logger.info("CliServer started  socketPath={}", path)
		
		scope.launch {
			while (channel.isOpen) {
				val client = runCatching { channel.accept() }.getOrNull() ?: break
				logger.info("Client connected  socketPath={}", socketPath())
				connectionLimit.acquire()
				activeClients.add(client)
				scope.launch {
					try {
						handle(client, router)
					} finally {
						activeClients.remove(client)
						connectionLimit.release()
					}
				}
			}
		}
	}
	
	fun stop() {
		activeClients.forEach { runCatching { it.close() } }
		activeClients.clear()
		runCatching { channel.close() }
		scope.cancel()
		runCatching { socketPath().deleteIfExists() }
		logger.info("CliServer stopped  socketPath={}", socketPath())
	}
	
	private suspend fun handle(client: SocketChannel, router: CommandRouter) {
		client.use {
			val line = readLine(client) ?: run {
				logger.debug("Client sent no data")
				return
			}
			val command = (json.decodeFromString<CliMessage>(line) as? CliMessage.Command) ?: run {
				logger.warn("Failed to parse CLI message")
				return
			}
			logger.debug("CliMessage received  command={}  argCount={}", command.command(), command.args.size)
			
			val prompt: suspend (text: String, echo: Boolean) -> String = { text, echo ->
				write(client, json.encodeToString<CliResponse>(CliResponse.Prompt(text, echo)))
				val line = readLine(client) ?: throw CancellationException("Client disconnected")
				val reply = json.decodeFromString<CliMessage>(line)
				(reply as? CliMessage.PromptResponse)?.text
					?: throw IllegalStateException("Expected PromptResponse, got ${reply::class.simpleName}")
			}
			
			var sawDone = false
			val cmdName = command.command()
			try {
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
			} catch (e: CancellationException) {
				throw e
			} catch (e: IOException) {
				logger.warn("Client disconnected during command  command={}", cmdName)
				trace.add("e", e.stackTraceToString())
			} catch (e: Exception) {
				logger.error("Command failed  command={}", cmdName, e)
				trace.add("e", e.stackTraceToString())
				runCatching {
					write(
						client, json.encodeToString<CliResponse>(
							CliResponse.Data(e.message ?: "Internal error", "stderr", true)
						)
					)
				}
			}
			
			if (!sawDone) {
				logger.warn("Command did not emit Done  command={}", cmdName)
				runCatching {
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
				logger.warn("Line exceeded max length  length={}", sb.length)
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
	
	companion object {
		private const val MAX_RESPONSE_CHUNK = 256 * 1024
		
		private fun socketPath(): Path = Path.of(
			System.getProperty("user.home"),
			".config", "autotweaker", "cli.sock",
		)
	}
}
