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

@file:Suppress("FunctionName")

package io.github.autotweaker.api.tool

import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.get
import io.github.autotweaker.api.types.config.SettingValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

fun <T> Ready(serializer: KSerializer<T>, result: T) =
	Tool.ResolveResult.Ready(Json.encodeToJsonElement(serializer, result))

fun Rejected(message: SettingDef<SettingValue.ValString>, vararg args: Any?) =
	Tool.ResolveResult.Rejected(message.get().format(*args))
