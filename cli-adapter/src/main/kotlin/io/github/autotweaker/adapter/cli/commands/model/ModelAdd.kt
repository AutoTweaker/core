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

import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class ModelAdd(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) {
	private val i18n: I18nService get() = core.i18n.i18nService
	
	fun addAll(providerType: String): Flow<CmdOutput> = flow {
		val provider = core.config.listAvailableProviderTypes().find { it == providerType } ?: run {
			emitI18n(i18n, ModelI18n.ProviderTypeNotFound())
			emitDone(1)
		}
	}
}
