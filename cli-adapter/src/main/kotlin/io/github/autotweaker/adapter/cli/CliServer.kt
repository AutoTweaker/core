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
import io.github.autotweaker.adapter.cli.console.Ansi
import io.github.autotweaker.adapter.cli.console.CmdOutput
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.*
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.exception.AutoTweakerException
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.io.bytestring.ByteString
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.deleteIfExists
import kotlin.time.Duration.Companion.seconds

object CliServer : Loggable, Traceable {
	val isRunning get() = ::serverSocket.isInitialized && !serverSocket.isClosed
	
	@AutoService(SettingDef::class)
	class MaxLineLength : IntSetting(
		10_485_760, zh(
			"CLI接收消息的最大行长度（字节），超出会断开连接，默认10_485_760即10MB"
		)
	)
	
	@AutoService(SettingDef::class)
	class ReadTimeout : IntSetting(
		15,
		zh("读取stdin输入时，首块数据到达前的等待时间（秒）")
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
				val requestId = ShortIdGenerator.nextString()
				val client = trace.catching { serverSocket.accept() }
					.onFailure {
						log.warn(
							"Failed connection acceptance  requestId={}  reason={}",
							requestId,
							it.message
						)
					}.getOrNull() ?: break
				log.info("Client connected  requestId={}", requestId)
				connectionLimit.acquire()
				activeClients.add(client)
				scope.launch {
					trace.catching {
						handle(client, requestId, router)
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
	
	private suspend fun handle(socket: Socket, requestId: String, router: CommandRouter) =
		socket.use {
			val readChannel = ByteChannel(false)
			val readerJob = socket.attachForReading(readChannel)
			val receiveChannel: ByteReadChannel = readChannel
			val sendChannel = socket.openWriteChannel(autoFlush = true)
			
			val line = receiveChannel.readCliLine() ?: run {
				log.warn("Client sent no data  requestId={}", requestId); return@use
			}
			val command = json.decodeFromString<CliMessage.Command>(line)
			
			log.debug(
				"Received CliMessage  command={}  requestId={}  argCount={}",
				command.command(),
				requestId,
				command.args.size
			)
			
			val cmdName = command.command()
			val done = AtomicBoolean(false)
			val disconnected = AtomicBoolean(false)
			val parentJob = currentCoroutineContext()[Job]
			readerJob.invokeOnCompletion {
				if (!done.get() && disconnected.compareAndSet(false, true)) {
					log.warn(
						"Client disconnected during command  command={}  requestId={}",
						cmdName, requestId
					)
					parentJob?.cancel(ClientDisconnectedException())
				}
			}
			
			val responseLock = ReentrantMutex()
			
			coroutineScope {
				val stdinChannel = Channel<String>(Channel.BUFFERED)
				val promptChannel = Channel<String?>(Channel.UNLIMITED)
				
				val timeoutJob = launch {
					delay(ReadTimeout().get().seconds)
					stdinChannel.close()
				}
				val messageJob = launch {
					while (true) {
						val line = receiveChannel.readCliLine() ?: break
						when (val msg = json.decodeFromString<CliMessage>(line)) {
							is CliMessage.Stdin -> {
								timeoutJob.cancel()
								trace.catching { stdinChannel.send(msg.chunk) }
							}
							
							is CliMessage.StdinEnd -> {
								log.debug("Received stdin EOF  command={}  requestId={}", cmdName, requestId)
								timeoutJob.cancel()
								stdinChannel.close()
							}
							
							is CliMessage.PromptResponse -> promptChannel.send(msg.text)
							is CliMessage.Command -> log.warn(
								"Unexpected Command message  command={}  requestId={}  received={}",
								cmdName, requestId, msg.command()
							)
						}
					}
				}
				val prompt: suspend (echo: Boolean) -> String? = { echo ->
					responseLock.withLock {
						sendChannel.writeResponse(CliResponse.Prompt(echo))
						promptChannel.receive()
					}
				}
				val output: suspend (CmdOutput) -> Unit = { (text, channel) ->
					responseLock.withLock {
						text.chunked(MAX_RESPONSE_CHUNK).forEach { part ->
							sendChannel.writeResponse(CliResponse.Data(part, channel))
						}
					}
				}
				
				trace.catching {
					val exitCode = router.dispatch(command, requestId, stdinChannel, prompt, output)
					done.set(true)
					log.info("Command finished  command={}  requestId={}  exitCode={}", cmdName, requestId, exitCode)
					responseLock.withLock {
						sendChannel.writeResponse(CliResponse.Done(exitCode))
					}
				}.also { messageJob.cancel() }.recoverException { e: ClientDisconnectedException ->
					if (disconnected.compareAndSet(false, true))
						log.warn(
							"Disconnected client during command  command={}  requestId={}  reason={}",
							cmdName, requestId, e.cause?.message ?: e.message
						)
				}.rethrowCancellation().onFailure { e ->
					if (e is AutoTweakerException)
						log.warn(
							"Command failed  command={}  requestId={}  exception={}  reason={}",
							cmdName, requestId,
							e::class.simpleName,
							e.message,
						)
					else log.error("Command failed  command={}  requestId={}", cmdName, requestId, e)
					done.set(true)
					responseLock.withLock {
						trace.catching {
							sendChannel.writeResponse(e.message().error(command.isTty))
							sendChannel.writeResponse(CliResponse.Done(1))
						}
					}
				}
			}
		}
	
	
	private suspend fun ByteReadChannel.readCliLine(): String? =
		trace.catching {
			val collected = ByteChannel(false)
			val count = readUntil(
				ByteString('\n'.code.toByte()),
				collected,
				limit = maxLineLength.toLong(),
				ignoreMissing = true
			)
			if (count == 0L) null
			else {
				collected.close()
				collected.readRemaining().readByteArray().decodeToString()
			}
		}.rethrowCancellation()
			.getOrElse { throw ClientDisconnectedException(it) }
	
	private suspend fun ByteWriteChannel.writeResponse(response: CliResponse) =
		trace.catching { writeStringUtf8(json.encodeToString(response) + '\n') }
			.rethrowCancellation()
			.getOrElse { throw ClientDisconnectedException(it) }
	
	private fun String.error(isTty: Boolean) = CliResponse.Data(
		"${
			if (isTty) Ansi.styled(this, Ansi.RED)
			else this
		}\n",
		OutputChannel.STDERR
	)
	
	private class ClientDisconnectedException(override val cause: Throwable? = null) :
		CancellationException("Client disconnected")
}
