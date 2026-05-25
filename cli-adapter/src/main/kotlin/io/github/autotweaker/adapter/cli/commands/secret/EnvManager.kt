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

package io.github.autotweaker.adapter.cli.commands.secret

import io.github.autotweaker.adapter.cli.CmdOutput
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n.I18nService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class EnvManager(
	private val core: CoreAPI, private val prompt: suspend (text: String, echo: Boolean) -> String
) {
	private val i18n: I18nService get() = core.i18n.i18nService
	
	fun list(type: EnvType): Flow<CmdOutput> = flow {}
	fun add(type: EnvType, name: String): Flow<CmdOutput> = flow {}
	fun get(type: EnvType, name: String): Flow<CmdOutput> = flow {}
	fun remove(type: EnvType, name: String): Flow<CmdOutput> = flow {}
	
	internal enum class EnvType { BASH, CONTAINER }
}