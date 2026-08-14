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

import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.api.types.tool.buildPresentation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

fun String.toolFail(presentation: MutableList<UiBlock>.() -> Unit) = toolResult(false, presentation)

fun String.toolSuccess(presentation: MutableList<UiBlock>.() -> Unit) = toolResult(true, presentation)

fun String.toolResult(success: Boolean, presentation: MutableList<UiBlock>.() -> Unit) =
	Tool.ToolOutput(this, buildPresentation(presentation), null, success)

fun <T> String.toolFail(serializer: KSerializer<T>, data: T, presentation: MutableList<UiBlock>.() -> Unit) =
	toolResult(serializer, data, false, presentation)

fun <T> String.toolSuccess(serializer: KSerializer<T>, data: T, presentation: MutableList<UiBlock>.() -> Unit) =
	toolResult(serializer, data, true, presentation)

fun <T> String.toolResult(
	serializer: KSerializer<T>,
	data: T,
	success: Boolean,
	presentation: MutableList<UiBlock>.() -> Unit
) = Tool.ToolOutput(
	this, buildPresentation(presentation),
	Json.encodeToJsonElement(serializer, data), success
)
