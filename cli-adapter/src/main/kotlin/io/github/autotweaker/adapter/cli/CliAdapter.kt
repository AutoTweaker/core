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
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.adapter.AdapterInfo

@AutoService(Adapter::class)
class CliAdapter : Adapter, Loggable {
	private val info by lazy {
		AdapterInfo(
			name = "cli-adapter".toKebab(),
			description = "AutoTweaker CLI Adapter",
			version = SemVer.parse("0.1.0"),
			source = "https://github.com/AutoTweaker/core".toUrl(),
		)
	}
	private lateinit var core: CoreAPI
	
	override val isRunning: Boolean get() = CliServer.isRunning
	
	override suspend fun init(core: CoreAPI) = info.also { this.core = core }
	
	override suspend fun start() {
		val router = CommandRouter.fromServiceLoader(core)
		CliServer.start(router)
		log.info("Started CliAdapter  version={}", info.version)
	}
	
	override suspend fun stop() {
		CliServer.stop()
		log.info("Stopped CliAdapter")
	}
}
