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
import kotlin.time.Duration

fun Rejected(reason: String, presentation: MutableList<UiBlock>.() -> Unit) =
	Tool.ResolveResult.Rejected(reason, buildPresentation(presentation))


inline fun <T> Ready(
	serializer: KSerializer<T>, result: T,
	crossinline request: MutableList<UiBlock>.(reason: String) -> Unit,
	crossinline executing: MutableList<UiBlock>.() -> Unit,
	crossinline cancelled: MutableList<UiBlock>.() -> Unit,
	crossinline rejected: MutableList<UiBlock>.(reason: String?) -> Unit,
	crossinline failed: MutableList<UiBlock>.(e: Throwable) -> Unit,
	crossinline timeout: MutableList<UiBlock>.(elapsed: Duration) -> Unit,
) = Tool.ResolveResult.Ready(
	Json.encodeToJsonElement(serializer, result),
	{ mutableListOf<UiBlock>().apply { request(it) } },
	{ mutableListOf<UiBlock>().apply(executing) },
	{ mutableListOf<UiBlock>().apply(cancelled) },
	{ mutableListOf<UiBlock>().apply { rejected(it) } },
	{ mutableListOf<UiBlock>().apply { failed(it) } },
	{ mutableListOf<UiBlock>().apply { timeout(it) } }
)
