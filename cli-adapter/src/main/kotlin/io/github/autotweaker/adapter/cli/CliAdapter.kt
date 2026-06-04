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
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import org.slf4j.LoggerFactory

@AutoService(Adapter::class)
class CliAdapter : Adapter {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val adapterVersion = SemVer.parse("0.1.0")
	private lateinit var server: CliServer
	private var coreVersion: SemVer? = null
	private lateinit var adapterName: String
	
	override suspend fun load(coreVersion: SemVer): AdapterInfo {
		this.coreVersion = coreVersion
		val info = AdapterInfo(
			name = "cli-adapter",
			description = "CLI adapter — Unix domain socket based command interface",
			version = adapterVersion,
			source = Url("https://github.com/AutoTweaker/core"),
		)
		adapterName = info.name
		logger.info(
			"CliAdapter loaded  adapter={}  version={}  coreVersion={}", adapterName, adapterVersion, coreVersion
		)
		return info
	}
	
	override suspend fun start(core: CoreAPI) {
		server = CliServer(core.config.settingService)
		val router = CommandRouter.fromServiceLoader(core, coreVersion ?: error("CliAdapter not initialized"))
		server.start(router)
		logger.info("CliAdapter started  adapter={}  version={}", adapterName, adapterVersion)
	}
	
	override suspend fun stop() {
		server.stop()
		logger.info("CliAdapter stopped  adapter={}", adapterName)
	}
}
