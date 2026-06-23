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
import io.github.autotweaker.api.*
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.config.SettingEntry
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Config : Command, Settable, I18nable, Traceable {
	@AutoService(SettingDef::class)
	class DefaultLimit : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(1000)
		override val description = "cfg命令的默认limit参数值"
	}
	
	override val name: String = "cfg"
	override val description: String
		get() = i18n.get(CfgI18n.Desc())
	override val syntax
		get() = Syntax.xor(
			Syntax.all(
				Syntax.xor(
					Syntax.leaf(Param.Type.FLAG, "list", CfgI18n.List()),
					Syntax.all(
						Syntax.leaf(Param.Type.VALUE, "search", CfgI18n.Search(), aliases = emptyList()),
						Syntax.xor(
							Syntax.leaf(Param.Type.FLAG, "key", CfgI18n.SearchKey(), aliases = emptyList()),
							Syntax.leaf(Param.Type.FLAG, "value", CfgI18n.SearchValue(), aliases = emptyList()),
							Syntax.leaf(Param.Type.FLAG, "desc", CfgI18n.SearchDesc(), aliases = emptyList()),
							required = false,
						),
					),
				),
				Syntax.leaf(Param.Type.VALUE, "limit", CfgI18n.Limit(), required = false, aliases = emptyList()),
				Syntax.leaf(Param.Type.FLAG, "full", CfgI18n.Full(), required = false),
			),
			Syntax.all(
				Syntax.leaf(Param.Type.VALUE, "set", CfgI18n.Set()),
				Syntax.leaf(Param.Type.POSITIONAL, "value", CfgI18n.SetValue()),
			),
			Syntax.all(
				Syntax.leaf(Param.Type.VALUE, "reset", CfgI18n.Yes()),
				Syntax.leaf(Param.Type.FLAG, "yes", CfgI18n.Yes(), required = false),
			)
		)
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val full: Boolean = request.get("full").toBoolean()
		val limit: Int = request.get("limit")?.toIntOrNull() ?: setting.get(DefaultLimit()).value
		
		if (request.has("list")) {
			emitAll(list(limit, full))
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
			emitAll(search(limit, full, query, mode))
			emitDone()
			return@flow
		}
		
		if (request.has("set")) {
			val key = request.get("set") ?: error("Missing key")
			val value = request.positional.firstOrNull() ?: error("Missing value")
			emitAll(set(key, value))
			return@flow
		}
		
		if (request.has("reset")) {
			val key = request.get("reset") ?: error("Missing key")
			val yes = request.has("yes")
			emitAll(reset(key, yes, prompt))
			return@flow
		}
		
		emitDone(1)
		return@flow
	}
	
	private fun list(limit: Int, full: Boolean = false): Flow<CmdOutput> = flow {
		val settings = setting.getAll().take(limit)
		printConfig(settings, full)
	}
	
	private fun search(
		limit: Int, full: Boolean = false, query: String, mode: SearchMode?
	): Flow<CmdOutput> = flow {
		val settings = setting.getAll()
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
		printConfig(result.take(limit), full)
	}
	
	private fun set(key: String, value: String): Flow<CmdOutput> = flow {
		val config = settingOrEmit(key) ?: return@flow
		val newValue = trace.catching { config.value.parse(value) }
			.getOrElse {
				emitI18n(CfgI18n.SetTypeError(), error = true)
				emitDone(1)
				return@flow
			}
		setting.set(key, newValue)
		emitDone()
	}
	
	private fun reset(
		key: String,
		yes: Boolean,
		prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		val config = settingOrEmit(key) ?: return@flow
		
		val sure: Boolean = if (!yes) {
			emitI18n(CfgI18n.ShowSetting())
			printConfig(listOf(config), full = true)
			val result = prompt(i18n.get(CfgI18n.SureReset()) + " ", true).trim()
			result == "y" || result == "yes"
		} else true
		
		
		if (sure) {
			val default = setting.getDefault(config.id) ?: run { emitDone(1); return@flow }
			setting.set(id = config.id, value = default.default)
			setting.setDescription(id = config.id, description = default.description)
			emitDone()
		} else {
			emitDone(1)
		}
	}
	
	private suspend fun FlowCollector<CmdOutput>.settingOrEmit(key: String): SettingEntry? =
		setting.getAll().find { it.id == key } ?: run {
			emitI18n(CfgI18n.ShowSetting(), key, error = true)
			emitDone(1)
		}.discard(null)
	
	private suspend fun FlowCollector<CmdOutput>.printConfig(
		settings: List<SettingEntry>, full: Boolean
	) = if (full) settings.forEachBetween(
		{
			emitI18n(CfgI18n.OutKey(), it.id)
			emitI18n(CfgI18n.OutDesc(), it.description)
			emitI18n(CfgI18n.OutValue(), it.value.value.toString())
		},
		between = { emit(CmdOutput.Data("-".repeat(10))) })
	else settings.forEach { emit(CmdOutput.Data(it.id)) }
	
	
	private fun match(text: String, query: String): Boolean {
		val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
		
		if (keywords.isEmpty()) return false
		
		return keywords.all { keyword ->
			text.contains(keyword, ignoreCase = true)
		}
	}
	
	enum class SearchMode {
		KEY, DESC, VALUE
	}
}
