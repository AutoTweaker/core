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
import io.github.autotweaker.adapter.cli.commands.*
import io.github.autotweaker.adapter.cli.commands.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.commands.model.Model.Companion.findModel
import io.github.autotweaker.adapter.cli.commands.model.ModelI18n
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

@AutoService(Command::class)
class Translate : Command, I18nable {
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
	
	override fun init(core: CoreAPI) {
		this.core = core
	}
	
	override fun handle(request: Request, prompt: suspend (text: String, echo: Boolean) -> String): Flow<CmdOutput> =
		flow {
			if (request.has("model")) {
				val model = findModel(request, core) ?: return@flow
				core.i18n.setTranslationModel(model)
				emitDone()
				return@flow
			}
			
			if (request.has("rm-model")) {
				core.i18n.setTranslationModel(null)
				emitDone()
				return@flow
			}
			
			if (request.has("language")) {
				val locale = Locale.forLanguageTag(request.get("language") ?: return@flow)
				if (locale.language.isEmpty()) {
					emitDone(1)
					return@flow
				}
				core.i18n.setLanguage(locale)
				emitDone()
				return@flow
			}
			
			core.i18n.startTranslation()
			emitDone()
		}
}
