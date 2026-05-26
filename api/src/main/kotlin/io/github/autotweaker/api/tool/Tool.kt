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

import io.github.autotweaker.api.config.SettingService
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject

interface Tool {
	val meta: Meta
	
	data class Meta(
		val name: String,
		val description: String,
		val functions: List<Function>,
	)
	
	data class Function(
		val name: String,
		val description: String,
		val parameters: Map<String, Property>,
	) {
		data class Property(
			val description: String,
			val required: Boolean,
			val valueType: ValueType
		) {
			sealed class ValueType {
				data class StringValue(val enum: List<String>? = null) : ValueType()
				data class NumberValue(val enum: List<Double>? = null) : ValueType()
				data class IntegerValue(val enum: List<Int>? = null) : ValueType()
				data object BooleanValue : ValueType()
				data class ArrayValue(val items: ValueType) : ValueType()
				data class ObjectValue(val properties: Map<String, ValueType>) : ValueType()
			}
		}
	}
	
	data class ToolInput(
		val functionName: String,
		val arguments: JsonObject,
		val service: SettingService,
		val outputChannel: Channel<RuntimeOutput>? = null,
	)
	
	data class RuntimeOutput(
		val content: String,
	)
	
	data class ToolOutput(
		val result: String,
		val success: Boolean
	)
	
	
	suspend fun execute(input: ToolInput): ToolOutput
}
