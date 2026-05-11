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

import com.google.auto.service.AutoService
import io.github.autotweaker.core.Url
import io.github.autotweaker.core.adapter.api.AdapterAPI
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.api.data.AdapterInfo
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

@AutoService(AdapterAPI::class)
class CliAdapter : AdapterAPI {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val adapterVersion = SemVer.parse("0.1.0")
	private val server = CliServer()
	private var coreVersion: SemVer? = null
	private lateinit var adapterName: String
	
	override fun load(coreVersion: SemVer): AdapterInfo {
		this.coreVersion = coreVersion
		val info = AdapterInfo(
			name = "cli-adapter",
			description = "CLI adapter — Unix domain socket based command interface",
			version = adapterVersion,
			source = Url("https://github.com/AutoTweaker/core"),
		)
		adapterName = info.name
		return info
	}
	
	override fun start(core: CoreAPI) {
		val router = CommandRouter(core, coreVersion ?: error("CliAdapter not initialized"))
		runBlocking { I18n.init(adapterName) }
		server.start(router)
		logger.info("CliAdapter started  adapter={}  version={}", adapterName, adapterVersion)
	}
	
	override fun stop() {
		server.stop()
		logger.info("CliAdapter stopped  adapter={}", adapterName)
	}
}
