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

import io.github.autotweaker.adapter.cli.client.expect.createSymbolicLink
import io.github.autotweaker.adapter.cli.client.expect.env
import io.github.autotweaker.api.APP_NAME_LOWERCASE
import io.github.autotweaker.api.orNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

object FsService {
	val configDir = Path(env("HOME"), ".config", APP_NAME_LOWERCASE)
	val fs = SystemFileSystem
	
	fun syncPlugins() {
		val pluginsDir = Path(configDir, "plugins")
		fs.createDirectories(pluginsDir)
		val target = Path(pluginsDir, "cli-adapter.jar")
		val source = Path("/", "usr", "share", APP_NAME_LOWERCASE, "cli-adapter.jar")
		if (target.isRegularFile()) return
		fs.delete(target, mustExist = false)
		createSymbolicLink(link = target, source = source)
	}
	
	fun writeProxyEnv() {
		val proxyVars = listOf(
			"https_proxy", "HTTPS_PROXY", "http_proxy", "HTTP_PROXY",
			"all_proxy", "ALL_PROXY", "no_proxy", "NO_PROXY",
		)
		val lines = proxyVars.mapNotNull { name ->
			env(name).orNull()?.let { "$name=$it" }
		}
		if (lines.isEmpty()) return
		fs.sink(Path(configDir, "env")).buffered().use { sink ->
			sink.writeString(lines.joinToString("\n", postfix = "\n"))
		}
	}
	
	fun Path.isRegularFile(): Boolean = fs.metadataOrNull(this)?.isRegularFile == true
}
