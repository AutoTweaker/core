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

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface Tool<Args : Any> {
	val argsSerializer: KSerializer<Args>
	val name: String
	val description: String
	
	suspend fun describe(): Map<KProperty1<*, *>, String> = emptyMap()
	suspend fun describeFunctions(): Map<KClass<*>, String> = emptyMap()
	
	suspend fun execute(args: Args, outputChannel: Channel<RuntimeOutput>? = null): ToolOutput
	
	data class RuntimeOutput(
		val content: String,
		val type: OutputType
	) {
		enum class OutputType {
			INFO,
			ERROR,
			STATUS
		}
	}
	
	data class ToolOutput(
		val result: String,
		val success: Boolean,
	)
}
