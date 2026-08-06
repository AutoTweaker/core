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

package io.github.autotweaker.adapter.cli.commands.translate

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.model.Model.Companion.findModel
import io.github.autotweaker.adapter.cli.commands.model.ModelI18n
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import java.util.*

@AutoService(Command::class)
class Translate : Command {
	lateinit var core: CoreAPI
	
	override val name = "translate"
	override val description = i18n(TranslateI18n.Desc())
	override val syntax = buildSyntax(XOR) {
		required = false
		all {
			flag("model", TranslateI18n.SetModelDesc())
			positional("provider", ModelI18n.ParamProvider())
			positional("model", ModelI18n.ParamName())
		}
		flag("rm-model", TranslateI18n.RemoveModelDesc())
		value("language", TranslateI18n.SetLanguageDesc())
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("model") {
			val model = findModel(core)
			core.i18n.setTranslationModel(model)
		}
		
		handleFlag("rm-model") {
			core.i18n.setTranslationModel(null)
		}
		
		handleValue("language") {
			val locale = Locale.forLanguageTag(it)
			if (locale.language.isEmpty() || locale.toLanguageTag() != it)
				error(TranslateI18n.InvalidLanguageTag(), it)
			core.i18n.setLanguage(locale)
		}
		
		core.i18n.startTranslation()
		done()
	}
}
