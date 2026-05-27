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

class CliServer(service: SettingService) {
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
				logger.debug("Client connected")
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
			val line = readLine(client) ?: return
			logger.debug("CliMessage received  request={}", line)
			val command = (json.decodeFromString<CliMessage>(line) as? CliMessage.Command) ?: return
			
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
						is CmdOutput.Data -> write(
							client, json.encodeToString<CliResponse>(
								CliResponse.Data(chunk.text, chunk.channel.name.lowercase(), chunk.newline)
							)
						)
						
						is CmdOutput.Done -> {
							sawDone = true
							write(client, json.encodeToString<CliResponse>(CliResponse.Done(chunk.exitCode)))
							return@collect
						}
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				logger.error("Command failed  command={}", cmdName, e)
				if (e !is IOException) {
					runCatching {
						write(
							client, json.encodeToString<CliResponse>(
								CliResponse.Data(e.message ?: "Internal error", "stderr", true)
							)
						)
					}
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
		val buf = ByteBuffer.allocate(256)
		val sb = StringBuilder()
		while (true) {
			buf.clear()
			val n = channel.read(buf)
			if (n == -1) return sb.toString().ifEmpty { null }
			buf.flip()
			val chunk = StandardCharsets.UTF_8.decode(buf).toString()
			val nl = chunk.indexOf('\n')
			if (nl >= 0) {
				sb.append(chunk, 0, nl)
				return sb.toString()
			}
			if (sb.length > maxLineLength) {
				logger.warn("Line exceeded max length  length={}", sb.length)
				return null
			}
			sb.append(chunk)
		}
	}
	
	private fun write(channel: SocketChannel, text: String) {
		if (text.isEmpty()) return
		val bytes = (text + "\n").toByteArray(StandardCharsets.UTF_8)
		var pos = 0
		while (pos < bytes.size) {
			pos += channel.write(ByteBuffer.wrap(bytes, pos, bytes.size - pos))
		}
	}
	
	companion object {
		private fun socketPath(): Path = Path.of(
			System.getProperty("user.home"),
			".config", "autotweaker", "cli.sock",
		)
	}
}
