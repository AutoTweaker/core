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

package io.github.autotweaker.adapter.cli.commands.config

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.*
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@AutoService(Command::class)
class Config : Command {
	@AutoService(SettingDef::class)
	class DefaultLimit : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(1000)
		override val description = "cfg命令的默认limit参数值"
	}
	
	lateinit var core: CoreAPI
	private val i18n: I18nService get() = core.i18n.i18nService
	
	override val name: String = "cfg"
	override val description: String
		get() = i18n.get(CfgI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.all(
				Syntax.xor(
					Syntax.all(
						Syntax.leaf(Param.Flag("list", i18n.get(CfgI18n.List())), required = true),
					),
					Syntax.all(
						Syntax.leaf(
							Param.Value("search", i18n.get(CfgI18n.Search()), aliases = emptyList()), required = true
						),
						Syntax.xor(
							Syntax.leaf(Param.Flag("key", i18n.get(CfgI18n.SearchKey()), aliases = emptyList())),
							Syntax.leaf(Param.Flag("value", i18n.get(CfgI18n.SearchValue()), aliases = emptyList())),
							Syntax.leaf(Param.Flag("desc", i18n.get(CfgI18n.SearchDesc()), aliases = emptyList())),
							required = false,
						),
					),
				),
				Syntax.leaf(Param.Value("limit", i18n.get(CfgI18n.Limit()), aliases = emptyList())),
				Syntax.leaf(Param.Flag("full", i18n.get(CfgI18n.Full()))),
			), Syntax.all(
				Syntax.leaf(Param.Value("set", i18n.get(CfgI18n.Set())), required = true),
				Syntax.leaf(Param.Positional("value", i18n.get(CfgI18n.SetValue())), required = true),
			)
		)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val full: Boolean = request.get("full").toBoolean()
		val limit: Int = try {
			request.get("limit")?.toInt() ?: core.config.settingService.get(DefaultLimit()).value
		} catch (_: Exception) {
			core.config.settingService.get(DefaultLimit()).value
		}
		
		if (request.has("list")) {
			emitAll(list(core, limit, full))
			emitDone()
			return@flow
		}
		
		if (request.has("search")) {
			val query: String = request.get("search") ?: error("Missing query")
			val mode = when {
				request.has("key") -> SearchMode.KEY
				request.has("value") -> SearchMode.VALUE
				request.has("desc") -> SearchMode.DESC
				else -> null
			}
			emitAll(search(core, limit, full, query, mode))
			emitDone()
			return@flow
		}
		
		if (request.has("set")) {
			val key = request.get("set") ?: error("Missing key")
			val value = request.positional.firstOrNull() ?: error("Missing value")
			emitAll(set(core, key, value))
			return@flow
		}
		
		emitDone(1)
		return@flow
	}
	
	private fun list(core: CoreAPI, limit: Int, full: Boolean = false): Flow<CmdOutput> {
		val settings = core.config.settingService.getAll().take(limit)
		return printConfig(settings, full).map { CmdOutput.Data(it) }
	}
	
	private fun search(
		core: CoreAPI, limit: Int, full: Boolean = false, query: String, mode: SearchMode?
	): Flow<CmdOutput> {
		val settings = core.config.settingService.getAll()
		val result = when (mode) {
			SearchMode.KEY -> settings.filter { match(it.id, query) }
			SearchMode.VALUE -> settings.filter { match(it.value.value.toString(), query) }
			SearchMode.DESC -> settings.filter { match(it.description, query) }
			null -> settings.filter {
				match(it.id, query) || match(
					it.value.value.toString(), query
				) || match(it.description, query)
			}
		}
		return printConfig(result.take(limit), full).map { CmdOutput.Data(it) }
	}
	
	private fun set(core: CoreAPI, key: String, value: String): Flow<CmdOutput> = flow {
		val config = core.config.settingService.getAll().find { it.id == key } ?: run {
			emitI18n(i18n, CfgI18n.SetNotFound(), key, error = true)
			emitDone(1)
			return@flow
		}
		val newValue = try {
			config.value.parse(value)
		} catch (_: Exception) {
			emitI18n(i18n, CfgI18n.SetTypeError(), error = true)
			emitDone(1)
			return@flow
		}
		core.config.settingService.set(key, newValue)
		emitDone()
	}
	
	private fun printConfig(settings: List<SettingEntry>, full: Boolean): Flow<String> = flow {
		if (full) {
			settings.forEachIndexed { index, setting ->
				emit(i18n.get(CfgI18n.OutKey()).format(sanitize(setting.id)))
				emit(i18n.get(CfgI18n.OutDesc()).format(sanitize(setting.description)))
				emit(i18n.get(CfgI18n.OutValue()).format(sanitize(setting.value.value.toString())))
				if (index != settings.lastIndex) emit("-".repeat(10))
			}
		} else {
			settings.forEach { emit(sanitize(it.id)) }
		}
	}
	
	private fun sanitize(text: String): String = text.replace(ANSI_PATTERN, "")
	
	private fun match(text: String, query: String): Boolean {
		val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
		
		if (keywords.isEmpty()) return false
		
		return keywords.all { keyword ->
			text.contains(keyword, ignoreCase = true)
		}
	}
	
	enum class SearchMode {
		KEY, DESC, VALUE
	}
	
	companion object {
		private val ANSI_PATTERN = Regex("\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])")
	}
}
