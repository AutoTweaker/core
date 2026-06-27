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
import io.github.autotweaker.adapter.cli.client.expect.printErr
import io.github.autotweaker.api.APP_NAME_LOWERCASE
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

fun main(args: Array<String>) {
	if (
		args.count() == 3 &&
		args.getOrNull(1) in setOf("-d", "--daemon") &&
		args.getOrNull(2) in KNOWN_DAEMON_ACTIONS
	) {
		systemctl("--user", "daemon-reload")
		val result = systemctl("--user", args[2], APP_NAME_LOWERCASE)
		printErr(result.output)
		exitProcess(result.exitCode)
	}
	
	fs.createDirectories(configDir)
	val sockPath = Path(configDir, "cli.sock")
	val lockPath = Path(configDir, "autotweaker.lock")
	
	syncPlugins(); writeProxyEnv()
	
	fun buildRequest(): String {
		val prog = args.firstOrNull()?.substringAfterLast('/') ?: APP_NAME_LOWERCASE
		val cmdArgs = args.drop(1).toList()
		return Json.encodeToString(CliMessage.Command(args = cmdArgs, prog = prog))
	}
	
	runBlocking {
		ensureDaemon()
		waitForReady(sockPath, lockPath)
		
		val request = buildRequest()
		val transport = Transport.connect(sockPath)
		try {
			transport.sendLine(request)
			exitProcess(Protocol(transport))
		} catch (e: Exception) {
			printErr("Error: ${e.message}\n")
			exitProcess(1)
		}
	}
}
