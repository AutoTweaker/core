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

package io.github.autotweaker.adapter.cli.commands.session.model

import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.model.Model.Companion.findModel
import io.github.autotweaker.adapter.cli.commands.model.ModelI18n
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.addFallback
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.removeFallback
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.setCompact
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.setMain
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.setSummarize
import io.github.autotweaker.adapter.cli.commands.session.model.ModelManager.setThinking
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n

class ModelSet : Command {
	override val name = "set"
	override val description = i18n(SessionModelI18n.SetDesc())
	override val syntax = buildSyntax(XOR) {
		value("thinking", SessionModelI18n.ThinkingParam()) { aliases("t", "think", "reasoning") }
		all {
			xor {
				required = false // 设置主模型
				flag("summarize", SessionModelI18n.SummarizeParam())
				flag("compact", SessionModelI18n.CompactParam())
				flag("add-fallback", SessionModelI18n.AddFallbackParam()) { aliases() }
				flag("remove-fallback", SessionModelI18n.RemoveFallbackParam()) { aliases() }
			}
			all {
				positional("provider", ModelI18n.ParamProvider())
				positional("model", ModelI18n.ParamName())
			}
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		ModelManager.init(core)
		handleValue("thinking") {
			val value = it.trim().lowercase()
			val enable = when (value) {
				"1", "true", "enable", "on" -> true
				"0", "false", "disable", "off" -> false
				else -> error(ModelI18n.InvalidValue())
			}
			setThinking(enable)
		}
		val providerName = getPositional(0)
		val modelName = getPositional(1)
		val model = findModel(core, providerName, modelName)
		handleFlag("summarize") {
			setSummarize(model)
		}
		handleFlag("compact") {
			setCompact(model)
		}
		handleFlag("add-fallback") {
			addFallback(model)
		}
		handleFlag("remove-fallback") {
			removeFallback(model)
		}
		setMain(model)
		done()
	}
}
