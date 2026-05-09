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

package io.github.autotweaker.core.adapter.impl.cli

import io.github.autotweaker.core.adapter.impl.cli.Command.Chunk
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class CliServer {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val json = Json { ignoreUnknownKeys = true }
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private lateinit var channel: ServerSocketChannel
	
	fun start(router: CommandRouter) {
		val path = socketPath()
		Files.createDirectories(path.parent)
		path.deleteIfExists()
		
		channel = ServerSocketChannel.open().apply {
			bind(UnixDomainSocketAddress.of(path))
		}
		logger.info("CliServer listening  path={}", path)
		
		scope.launch {
			while (channel.isOpen) {
				val client = runCatching { channel.accept() }.getOrNull() ?: break
				scope.launch { handle(client, router) }
			}
		}
	}
	
	fun stop() {
		scope.cancel()
		runCatching { channel.close() }
		runCatching { socketPath().deleteIfExists() }
		logger.info("CliServer stopped")
	}
	
	private suspend fun handle(client: SocketChannel, router: CommandRouter) {
		client.use {
			client.configureBlocking(false)
			val line = readLine(client) ?: return
			val request = json.decodeFromString<Request>(line)
			
			val prompt: suspend (String) -> String = { text ->
				write(client, text)
				readLine(client) ?: ""
			}
			
			router.dispatch(request, prompt).collect { chunk ->
				when (chunk) {
					is Chunk.Data -> write(client, chunk.text)
					is Chunk.Done -> return@collect
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
			if (n == 0) continue
			buf.flip()
			val chunk = StandardCharsets.UTF_8.decode(buf).toString()
			val nl = chunk.indexOf('\n')
			if (nl >= 0) {
				sb.append(chunk, 0, nl)
				return sb.toString()
			}
			sb.append(chunk)
		}
	}
	
	private fun write(channel: SocketChannel, text: String) {
		if (text.isEmpty()) return
		val bytes = text.toByteArray(StandardCharsets.UTF_8)
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