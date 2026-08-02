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

package io.github.autotweaker.api.tool

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

fun String.toolFail() = toolResult(false)

fun String.toolSuccess() = toolResult(true)

fun String.toolResult(success: Boolean) = Tool.ToolOutput(this, null, success)

fun <T> String.toolFail(data: T?, serializer: KSerializer<T>) =
	toolResult(data, serializer, false)

fun <T> String.toolSuccess(data: T?, serializer: KSerializer<T>) =
	toolResult(data, serializer, true)

fun <T> String.toolResult(data: T?, serializer: KSerializer<T>, success: Boolean) =
	Tool.ToolOutput(this, data?.let { Json.encodeToJsonElement(serializer, it) }, success)
