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
import io.github.autotweaker.adapter.cli.console.CmdOutput
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.*
import io.github.autotweaker.api.config.SettingDef
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.deleteIfExists

object CliServer : Loggable, Traceable {
	val isRunning get() = ::serverSocket.isInitialized && !serverSocket.isClosed
	
	@AutoService(SettingDef::class)
	class MaxLineLength : IntSetting(
		10_485_760, zh(
			"CLI接收消息的最大行长度（字节），超出会断开连接，默认10_485_760即10MB"
		)
	)
	
	private val maxLineLength = MaxLineLength().get()
	
	private val json = Json { ignoreUnknownKeys = true }
	private val scope = scope(IO)
	
	private val activeClients = ConcurrentHashMap.newKeySet<Socket>()
	private val connectionLimit = Semaphore(64)
	
	private lateinit var serverSocket: ServerSocket
	private lateinit var selectorManager: SelectorManager
	
	private const val MAX_RESPONSE_CHUNK = 256 * 1024
	
	private val socketPath: Path = CONFIG_PATH.resolve("cli.sock")
	
	private val lock = ReentrantMutex()
	
	suspend fun start(router: CommandRouter) = lock.withLock {
		Files.createDirectories(socketPath.parent)
		socketPath.deleteIfExists()
		
		selectorManager = SelectorManager(Dispatchers.IO)
		serverSocket = aSocket(selectorManager).tcp().bind(UnixSocketAddress(socketPath.toString()))
		
		Files.setPosixFilePermissions(socketPath, PosixFilePermissions.fromString("rwx------"))
		log.info("Started CliServer  socketPath={}", socketPath)
		
		scope.launch {
			while (!serverSocket.isClosed) {
				val client = trace.catching { serverSocket.accept() }
					.onFailure {
						log.warn(
							"Failed connection acceptance  socketPath={}  reason={}",
							socketPath,
							it.message
						)
					}.getOrNull() ?: break
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
	
	suspend fun stop() = lock.withLock {
		activeClients.forEach { trace.catching { it.close() } }
		activeClients.clear()
		trace.catching { serverSocket.close() }
		trace.catching { selectorManager.close() }
		scope.cancel()
		trace.catching { socketPath.deleteIfExists() }
		log.info("Stopped CliServer  socketPath={}", socketPath)
	}
	
	private suspend fun handle(socket: Socket, router: CommandRouter) =
		socket.use {
			val readChannel = ByteChannel(false)
			val readerJob = socket.attachForReading(readChannel)
			val receiveChannel: ByteReadChannel = readChannel
			val sendChannel = socket.openWriteChannel(autoFlush = true)
			
			val line = receiveChannel.readCliLine() ?: run {
				log.warn("Client sent no data"); return@use
			}
			val command = json.decodeFromString<CliMessage.Command>(line)
			
			log.debug("Received CliMessage  command={}  argCount={}", command.command(), command.args.size)
			
			val cmdName = command.command()
			val done = AtomicBoolean(false)
			val disconnected = AtomicBoolean(false)
			val parentJob = currentCoroutineContext()[Job]
			readerJob.invokeOnCompletion {
				if (!done.get() && disconnected.compareAndSet(false, true)) {
					log.warn(
						"Client disconnected during command  command={}  reason=connection closed by peer",
						cmdName
					)
					parentJob?.cancel(CancellationException("Client disconnected"))
				}
			}
			
			val responseLock = ReentrantMutex()
			val prompt: suspend (echo: Boolean) -> String = { echo ->
				responseLock.withLock {
					sendChannel.writeResponse(CliResponse.Prompt(echo))
					
					val line = receiveChannel.readCliLine()
						?: throw CancellationException("Client disconnected")
					json.decodeFromString<CliMessage.PromptResponse>(line).text
				}
			}
			val output: suspend (CmdOutput) -> Unit = { (text, channel) ->
				responseLock.withLock {
					text.chunked(MAX_RESPONSE_CHUNK).ifEmpty { listOf("") }.forEach { part ->
						sendChannel.writeResponse(CliResponse.Data(part, channel))
					}
				}
			}
			
			trace.catching {
				val exitCode = router.dispatch(command, prompt, output)
				done.set(true)
				log.debug("Command finished  exitCode={}", exitCode)
				responseLock.withLock {
					sendChannel.writeResponse(CliResponse.Done(exitCode))
				}
			}.rethrowCancellation()
				.recoverException { e: IOException ->
					if (disconnected.compareAndSet(false, true))
						log.warn(
							"Disconnected client during command  command={}  reason={}",
							cmdName, e.message
						)
				}.onFailure { e ->
					log.error("Command failed  command={}", cmdName, e)
					responseLock.withLock {
						trace.catching {
							sendChannel.writeResponse(
								CliResponse.Data("Error: ${e.message()}\n", OutputChannel.STDERR)
							)
							sendChannel.writeResponse(CliResponse.Done(1))
						}
					}
				}
		}
	
	
	private suspend fun ByteReadChannel.readCliLine(): String? =
		trace.catching { readLineStrict(limit = maxLineLength.toLong()) }
			.onException<TooLongLineException> {
				log.warn("Exceeded line max length  limit={}", maxLineLength)
			}.getOrNull()
	
	private suspend fun ByteWriteChannel.writeResponse(response: CliResponse) =
		writeStringUtf8(json.encodeToString(response) + "\n")
}
