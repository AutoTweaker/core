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

package io.github.autotweaker.adapter.cli.commands.model

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n
import java.util.*

@AutoService(Command::class)
class Model : Command, Traceable {
	override val name = "model"
	override val description = i18n(ModelI18n.Description())
	override val syntax = buildSyntax(XOR) {
		flag("list", ModelI18n.ParamList())
		all {
			flag("add", ModelI18n.ParamAdd())
			value("name", ModelI18n.ParamName())
			value("provider", ModelI18n.ParamProvider())
			value("info", ModelI18n.ParamAddInfo()) { required = false }
		}
		value("add-all", ModelI18n.ParamAddAll()) { aliases() }
		
		all {
			xor {
				flag("remove", ModelI18n.ParamRemove()) { aliases("rm") }
				flag("set-default", ModelI18n.ParamDefault()) { aliases() }
			}
			positional("provider", ModelI18n.ParamProvider())
			positional("model", ModelI18n.ParamName())
		}
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("list") {
			list(core)
		}
		
		val add = ModelAdd(core)
		
		handleValue("add-all") {
			with(add) {
				addAll(it)
			}
		}
		
		handleFlag("add") {
			val name: String = getValue("name")
			val provider: String = getValue("provider")
			val info: String? = getValueOrNull("info")
			with(add) {
				add(name, provider, info)
			}
		}
		
		handleFlag("remove") {
			core.config.removeModel(findModel(core))
		}
		
		handleFlag("set-default") {
			core.config.setDefaultModel(findModel(core))
		}
		
		done(1)
	}
	
	private suspend fun Console.list(core: CoreAPI): Nothing {
		val provider = core.config.listProviders()
		core.config.listModels().forEach { model ->
			val providerName =
				provider.find { it.id == model.data.providerId }?.displayName ?: i18n(ModelI18n.Unknown())
			out("[$providerName] ${model.data.displayName}")
		}
		done()
	}
	
	companion object : I18nable {
		suspend fun Console.findModel(core: CoreAPI): UUID {
			val provider = getPositional(0)
			val model = getPositional(1)
			val providerId = core.config.listProviders().find { it.displayName == provider }?.id
				?: error(ModelI18n.ProviderNotFound(), provider)
			val modelId = core.config.listModels()
				.find { it.data.displayName == model && it.data.providerId == providerId }?.data?.id
				?: error(ModelI18n.ModelNotFound(), model)
			return modelId
		}
	}
}
