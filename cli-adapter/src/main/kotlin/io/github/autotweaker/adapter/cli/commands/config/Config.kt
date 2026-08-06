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
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue


@AutoService(Command::class)
class Config : Command, Traceable {
	@AutoService(SettingDef::class)
	class DefaultLimit : IntSetting(
		500, zh(
			"cfg命令的默认limit参数值"
		)
	)
	
	override val name: String = "cfg"
	override val description: String = i18n(CfgI18n.Desc())
	override val syntax = buildSyntax(XOR) {
		all {
			xor {
				flag("list", CfgI18n.List())
				all {
					value("search", CfgI18n.Search()) { aliases() }
					xor {
						required = false
						flag("key", CfgI18n.SearchKey()) { aliases() }
						flag("value", CfgI18n.SearchValue()) { aliases() }
						flag("desc", CfgI18n.SearchDesc()) { aliases() }
					}
				}
			}
			value("limit", CfgI18n.Limit()) {
				required = false
				aliases()
			}
			flag("full", CfgI18n.Full()) { required = false }
		}
		all {
			value("set", CfgI18n.Set())
			positional("value", CfgI18n.SetValue())
		}
		all {
			value("reset", CfgI18n.Reset())
			flag("yes", CfgI18n.Yes()) { required = false }
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleValue("set") {
			val value = getPositional(0)
			set(it, value, core)
		}
		
		handleValue("reset") {
			reset(it, hasArg("yes"), core)
		}
		
		val full: Boolean = hasArg("full")
		val limit: Int = getValueOrNull("limit")?.toIntOrNull() ?: DefaultLimit().get()
		
		handleFlag("list") {
			list(limit, full, core)
		}
		
		handleValue("search") {
			val mode = when {
				hasArg("key") -> SearchMode.KEY
				hasArg("value") -> SearchMode.VALUE
				hasArg("desc") -> SearchMode.DESC
				else -> null
			}
			search(limit, full, it, mode, core)
		}
		
		done(1)
	}
	
	private suspend fun Console.list(limit: Int, full: Boolean, core: CoreAPI) {
		val settings = core.config.getAllSettings().take(limit)
		printConfig(settings, core, full)
	}
	
	private suspend fun Console.search(
		limit: Int, full: Boolean, query: String, mode: SearchMode?, core: CoreAPI
	) {
		val settings = core.config.getAllSettings()
		val result = when (mode) {
			SearchMode.KEY -> settings.filter { match(it.id, query) }
			SearchMode.VALUE -> settings.filter { match(it.value.value.toString(), query) }
			SearchMode.DESC -> settings.filter { match(core.i18n.getString(it.id), query) }
			null -> settings.filter {
				match(it.id, query) || match(
					it.value.value.toString(), query
				)
			}
		}
		printConfig(result.take(limit), core, full)
	}
	
	private suspend fun Console.set(key: String, value: String, core: CoreAPI) {
		val config = getSetting(key, core)
		val newValue = trace.catching { config.value.parse(value) }.getOrElse {
			error(CfgI18n.SetTypeError())
		}
		core.config.setSetting(key, newValue)
	}
	
	private suspend fun Console.reset(
		key: String, yes: Boolean, core: CoreAPI
	) {
		val config = getSetting(key, core)
		
		if (!yes) {
			out(CfgI18n.ResetConfirmShowing())
			printConfig(listOf(config), core, full = true)
			if (!confirm(CfgI18n.ResetConfirm())) done(1)
		}
		
		val default = core.config.getSettingDef(config.id) ?: done(1)
		core.config.setSetting(config.id, default.default)
	}
	
	private suspend fun Console.printConfig(
		settings: List<SettingEntry>, core: CoreAPI, full: Boolean
	) = if (full) settings.forEachBetween({
		out(CfgI18n.OutKey(), it.id)
		out(CfgI18n.OutDesc(), core.i18n.getString(it.id))
		out(CfgI18n.OutType(), it.value.type())
		out(CfgI18n.OutValue(), it.value.value)
	}, between = { out(LINE) })
	else settings.forEach { out(it.id) }
	
	private fun SettingValue<*>.type() = when (this) {
		is SettingValue.ValBoolean -> "Boolean"
		is SettingValue.ValByte -> "Byte"
		is SettingValue.ValChar -> "Char"
		is SettingValue.ValDouble -> "Double"
		is SettingValue.ValFloat -> "Float"
		is SettingValue.ValInt -> "Int"
		is SettingValue.ValLong -> "Long"
		is SettingValue.ValShort -> "Short"
		is SettingValue.ValString -> "String"
	}
	
	private suspend fun Console.getSetting(id: String, core: CoreAPI) =
		core.config.getAllSettings().find { it.id == id } ?: error(CfgI18n.SettingNotFound(), id)
	
	private fun match(text: String, query: String): Boolean {
		val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
		
		return keywords.isNotEmpty() && keywords.all { keyword ->
			text.contains(keyword, ignoreCase = true)
		}
	}
	
	enum class SearchMode {
		KEY, DESC, VALUE
	}
}
