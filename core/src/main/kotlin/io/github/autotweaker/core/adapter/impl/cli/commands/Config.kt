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

package io.github.autotweaker.core.adapter.impl.cli.commands

import com.google.auto.service.AutoService
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Param
import io.github.autotweaker.core.adapter.impl.cli.Request
import io.github.autotweaker.core.adapter.impl.cli.Syntax
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.*

@AutoService(Command::class)
class Config : Command {
	lateinit var core: CoreAPI
	
	override val name: String = "cfg"
	override val description: String
		get() = I18n.get("cfg.desc")
	override val syntax
		get() = Syntax.xor(
			Syntax.all(
				Syntax.xor(
					Syntax.all(
						Syntax.leaf(Param.Flag("list", I18n.get("cfg.list")), required = true),
					),
					Syntax.all(
						Syntax.leaf(
							Param.Value("search", I18n.get("cfg.search"), aliases = emptyList()), required = true
						),
						Syntax.xor(
							Syntax.leaf(Param.Flag("key", I18n.get("cfg.search.key"), aliases = emptyList())),
							Syntax.leaf(Param.Flag("value", I18n.get("cfg.search.value"), aliases = emptyList())),
							Syntax.leaf(Param.Flag("desc", I18n.get("cfg.search.desc"), aliases = emptyList())),
							required = false,
						),
					),
				),
				Syntax.leaf(Param.Value("limit", I18n.get("cfg.limit"), aliases = emptyList())),
				Syntax.leaf(Param.Flag("full", I18n.get("cfg.full"))),
			), Syntax.all(
				Syntax.leaf(Param.Value("set", I18n.get("cfg.set")), required = true),
				Syntax.leaf(Param.Positional("value", I18n.get("cfg.set.value")), required = true),
			)
		)
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<Command.Chunk> = flow {
		val full: Boolean = request.get("full").toBoolean()
		val limit: Int = try {
			request.get("limit")?.toInt() ?: DEFAULT_LIMIT
		} catch (_: Exception) {
			DEFAULT_LIMIT
		}
		
		if (request.has("list")) {
			emitAll(list(core, limit, full))
			emit(Command.Chunk.Done())
			return@flow
		}
		
		if (request.has("search")) {
			val query: String = request.get("search") ?: error("Missing query")
			val mode = when {
				request.has("key") -> SearchMode.KEY
				request.has("value") -> SearchMode.VALUE
				request.has("desc") -> SearchMode.DESC
				else -> SearchMode.VALUE
			}
			emitAll(search(core, limit, full, query, mode))
			emit(Command.Chunk.Done())
			return@flow
		}
		
		if (request.has("set")) {
			val key = request.get("set") ?: error("Missing key")
			val value = request.positional.firstOrNull() ?: error("Missing value")
			emitAll(set(core, key, value))
			return@flow
		}
		
		emit(Command.Chunk.Done(1))
		return@flow
	}
	
	private fun list(core: CoreAPI, limit: Int, full: Boolean = false): Flow<Command.Chunk> {
		val settings = core.config.getAllAppConfigs().take(limit)
		return printConfig(settings, full).map { Command.Chunk.Data(it) }
	}
	
	private fun search(
		core: CoreAPI, limit: Int, full: Boolean = false, query: String, mode: SearchMode
	): Flow<Command.Chunk> {
		val settings = core.config.getAllAppConfigs()
		val result = when (mode) {
			SearchMode.KEY -> settings.filter { match(it.setting.key.value, query) }
			SearchMode.VALUE -> settings.filter { match(it.setting.value.value.toString(), query) }
			SearchMode.DESC -> settings.filter { match(it.setting.description, query) }
		}
		return printConfig(result.take(limit), full).map { Command.Chunk.Data(it) }
	}
	
	private fun set(core: CoreAPI, key: String, value: String): Flow<Command.Chunk> {
		val config = core.config.getAppConfig(SettingKey(key))?.setting ?: return flowOf(
			Command.Chunk.Data(I18n.get("cfg.set.not_found", key)), Command.Chunk.Done(1)
		)
		val new = config.copy(
			value = try {
				config.value.parse(value)
			} catch (_: Exception) {
				return flowOf(
					Command.Chunk.Data(I18n.get("cfg.set.type_error")), Command.Chunk.Done(1)
				)
			}
		)
		core.config.setAppConfig(CoreConfig.AppConfig(new))
		return flowOf(
			Command.Chunk.Done()
		)
	}
	
	private fun printConfig(settings: List<CoreConfig.AppConfig>, full: Boolean): Flow<String> = flow {
		if (full) {
			settings.forEachIndexed { index, setting ->
				emit(I18n.get("cfg.out.key", sanitize(setting.setting.key.value)))
				emit(I18n.get("cfg.out.desc", sanitize(setting.setting.description)))
				emit(I18n.get("cfg.out.val", sanitize(setting.setting.value.value.toString())))
				if (index != settings.lastIndex) emit("-".repeat(10))
			}
		} else {
			settings.forEach { emit(sanitize(it.setting.key.value)) }
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
		private const val DEFAULT_LIMIT = 1000
		private val ANSI_PATTERN = Regex("\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])")
	}
}
