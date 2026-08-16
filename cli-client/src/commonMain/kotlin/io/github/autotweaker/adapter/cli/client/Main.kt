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

package io.github.autotweaker.adapter.cli.client

import io.github.autotweaker.adapter.cli.CliMessage
import io.github.autotweaker.adapter.cli.client.DaemonManager.KNOWN_DAEMON_ACTIONS
import io.github.autotweaker.adapter.cli.client.DaemonManager.ensureDaemon
import io.github.autotweaker.adapter.cli.client.DaemonManager.systemctl
import io.github.autotweaker.adapter.cli.client.DaemonManager.waitForReady
import io.github.autotweaker.adapter.cli.client.FsService.configDir
import io.github.autotweaker.adapter.cli.client.FsService.fs
import io.github.autotweaker.adapter.cli.client.FsService.syncPlugins
import io.github.autotweaker.adapter.cli.client.FsService.writeProxyEnv
import io.github.autotweaker.adapter.cli.client.expect.*
import io.github.autotweaker.api.APP_NAME_LOWERCASE
import io.github.autotweaker.api.buildMessage
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

fun main(args: Array<String>) {
	if (
		args.count() == 2 &&
		args.getOrNull(0) in setOf("-d", "--daemon") &&
		args.getOrNull(1) in KNOWN_DAEMON_ACTIONS
	) {
		systemctl("--user", "daemon-reload")
		val result = systemctl("--user", args[1], APP_NAME_LOWERCASE)
		printErr(result.output)
		exitProcess(result.exitCode)
	}
	
	fs.createDirectories(configDir)
	val sockPath = Path(configDir, "cli.sock")
	val lockPath = Path(configDir, "autotweaker.lock")
	
	syncPlugins(); writeProxyEnv()
	
	fun buildRequest(): String {
		val prog = APP_NAME_LOWERCASE
		val cmdArgs = args.toList()
		return Json.encodeToString<CliMessage>(
			CliMessage.Command(args = cmdArgs, prog = prog, isTty = stdoutIsTty(), cwd = cwd())
		)
	}
	
	runBlocking {
		try {
			ensureDaemon()
			val transport = waitForReady(sockPath, lockPath)
			
			val request = buildRequest()
			transport.sendLine(request)
			val stdinScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
			stdinScope.launch {
				streamStdin(transport)
			}
			exitProcess(Protocol(transport))
		} catch (e: Exception) {
			printErr("Error: ${e.buildMessage()}\n")
			exitProcess(1)
		}
	}
}

private const val STDIN_CHUNK_SIZE = 8192

private suspend fun streamStdin(transport: Transport) {
	if (stdinIsTty()) {
		transport.sendLine(Json.encodeToString<CliMessage>(CliMessage.StdinEnd))
		return
	}
	val buffer = ByteArray(STDIN_CHUNK_SIZE)
	var pending = byteArrayOf()
	while (true) {
		val n = readStdinChunk(buffer)
		if (n <= 0) break
		val (text, tail) = splitUtf8(pending + buffer.copyOf(n))
		pending = tail
		if (text.isNotEmpty()) transport.sendLine(Json.encodeToString<CliMessage>(CliMessage.Stdin(text)))
	}
	if (pending.isNotEmpty()) transport.sendLine(Json.encodeToString<CliMessage>(CliMessage.Stdin(pending.decodeToString())))
	transport.sendLine(Json.encodeToString<CliMessage>(CliMessage.StdinEnd))
}

private fun splitUtf8(bytes: ByteArray): Pair<String, ByteArray> {
	var end = bytes.size
	while (end > 0 && (bytes[end - 1].toInt() and 0xC0) == 0x80) end--
	if (end == 0) return Pair("", bytes)
	val lead = bytes[end - 1].toInt() and 0xFF
	val expectedLen = when {
		(lead and 0xE0) == 0xC0 -> 2
		(lead and 0xF0) == 0xE0 -> 3
		(lead and 0xF8) == 0xF0 -> 4
		else -> 1
	}
	val tail = bytes.size - end + 1
	return if (tail < expectedLen) Pair(bytes.copyOf(end - 1).decodeToString(), bytes.copyOfRange(end - 1, bytes.size))
	else Pair(bytes.decodeToString(), byteArrayOf())
}
