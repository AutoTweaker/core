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

import io.github.autotweaker.adapter.cli.client.FsService.isRegularFile
import io.github.autotweaker.adapter.cli.client.expect.exec
import io.github.autotweaker.adapter.cli.client.expect.isSocket
import io.github.autotweaker.adapter.cli.client.expect.printErr
import io.github.autotweaker.api.APP_NAME_LOWERCASE
import kotlinx.coroutines.delay
import kotlinx.io.files.Path
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

object DaemonManager {
	val KNOWN_DAEMON_ACTIONS = setOf(
		"start", "stop", "restart", "reload", "try-restart",
		"enable", "disable", "mask", "unmask", "reenable",
		"status", "is-active", "is-enabled", "is-failed",
	)
	
	suspend fun ensureDaemon() {
		if (daemonIsActive()) return
		printErr("Starting daemon...\n")
		systemctl("--user", "reset-failed", APP_NAME_LOWERCASE)
		systemctl("--user", "start", APP_NAME_LOWERCASE)
		delay(500.milliseconds)
		if (!daemonIsActive()) {
			val sub = daemonSubstate()
			if (sub == "failed" || sub == "dead") {
				showJournalLogs(30)
				printErr("\n\nDaemon failed to start.")
				exitProcess(1)
			}
		}
	}
	
	suspend fun waitForReady(sockPath: Path, lockPath: Path) {
		var waited = 0
		while (true) {
			if (sockPath.isSocket() && lockPath.isRegularFile()) return
			val sub = daemonSubstate()
			if (!daemonIsActive() || sub == "failed" || sub == "auto-restart") {
				showJournalLogs(30)
				printErr("\n\nDaemon failed to start.")
				exitProcess(1)
			}
			waited++
			if (waited >= 600) {
				showJournalLogs(15)
				printErr("\n\nDaemon is taking too long.")
				waited = 0
			}
			delay(100.milliseconds)
		}
	}
	
	private fun daemonIsActive(): Boolean {
		val result = systemctl("--user", "show", APP_NAME_LOWERCASE, "-p", "ActiveState", "--value")
		return result.output.trim() == "active"
	}
	
	private fun daemonSubstate(): String {
		val result = systemctl("--user", "show", APP_NAME_LOWERCASE, "-p", "SubState", "--value")
		return result.output.trim()
	}
	
	private fun showJournalLogs(lines: Int) {
		val result = exec(
			"journalctl", "--user", "-u", APP_NAME_LOWERCASE, "--no-pager", "-n", lines.toString()
		)
		if (result.output.isNotEmpty()) printErr(result.output + "\n")
	}
	
	fun systemctl(vararg args: String) = exec("systemctl", *args)
}
