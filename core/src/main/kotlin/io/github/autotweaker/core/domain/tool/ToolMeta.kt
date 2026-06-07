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

package io.github.autotweaker.core.domain.tool

import io.github.autotweaker.api.tool.Tool
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

class ToolMeta private constructor(
	val name: String,
	val description: String,
	val functions: List<Function>,
) {
	class Function(
		val name: String,
		val description: String,
		val parameters: Map<String, Property>,
	)
	
	class Property(
		val description: String,
		val required: Boolean,
		val valueType: ValueType,
	)
	
	sealed class ValueType {
		data class StringValue(val enum: List<String>? = null) : ValueType()
		data class NumberValue(val enum: List<Double>? = null) : ValueType()
		data class IntegerValue(val enum: List<Int>? = null) : ValueType()
		data object BooleanValue : ValueType()
		data class ArrayValue(val items: ValueType) : ValueType()
		data class ObjectValue(val properties: Map<String, ValueType>) : ValueType()
	}
	
	companion object {
		private fun String.toSnakeCase() = convertCamelCase(this, '_')
		
		@OptIn(ExperimentalSerializationApi::class)
		suspend fun build(tool: Tool<*>): ToolMeta {
			require('-' !in tool.name) { "Tool name must not contain '-': ${tool.name}" }
			val desc = tool.argsSerializer.descriptor
			val describeMap = tool.describe()
			return if (desc.kind == PolymorphicKind.SEALED) {
				buildSealed(tool, desc, describeMap, tool.describeFunctions())
			} else {
				buildSingle(tool, desc, describeMap)
			}
		}
		
		private fun buildSingle(
			tool: Tool<*>,
			desc: SerialDescriptor,
			describeMap: Map<KProperty1<*, *>, String>,
		): ToolMeta {
			val descByName = describeMap.entries.associate { (prop, d) -> prop.name to d }
			val params = (0 until desc.elementsCount).associate { i ->
				val fieldName = desc.getElementName(i)
				fieldName.toSnakeCase() to Property(
					description = descByName[fieldName] ?: error("Missing description for '$fieldName'"),
					required = !desc.isElementOptional(i),
					valueType = desc.getElementDescriptor(i).toValueType(),
				)
			}
			return ToolMeta(tool.name, tool.description, listOf(Function("run", tool.description, params)))
		}
		
		private fun buildSealed(
			tool: Tool<*>,
			desc: SerialDescriptor,
			describeMap: Map<KProperty1<*, *>, String>,
			funcDescMap: Map<KClass<*>, String>,
		): ToolMeta {
			val grouped = describeMap.entries.groupBy { (prop, _) ->
				prop.javaField?.declaringClass
			}
			
			val functions = (0 until desc.elementsCount).map { i ->
				val subDesc = desc.getElementDescriptor(i)
				val funcName = desc.getElementName(i)
				require('-' !in funcName) { "Function name must not contain '-': $funcName" }
				
				val ownerClass = grouped.keys.first { clazz ->
					clazz?.simpleName == funcName
				}
				val funcEntries = grouped[ownerClass]!!
				val descByName = funcEntries.associate { (prop, d) -> prop.name to d }
				val funcDesc = funcDescMap[ownerClass?.kotlin]
					?: error("Missing function description for '$funcName' in describeFunctions()")
				
				val params = (0 until subDesc.elementsCount).associate { j ->
					val fieldName = subDesc.getElementName(j)
					fieldName.toSnakeCase() to Property(
						description = descByName[fieldName]
							?: error("Missing description for '$fieldName' in '$funcName'"),
						required = !subDesc.isElementOptional(j),
						valueType = subDesc.getElementDescriptor(j).toValueType(),
					)
				}
				Function(funcName.toSnakeCase(), funcDesc, params)
			}
			return ToolMeta(tool.name, tool.description, functions)
		}
		
		@OptIn(ExperimentalSerializationApi::class)
		private fun SerialDescriptor.toValueType(): ValueType = when (kind) {
			PrimitiveKind.STRING, PrimitiveKind.CHAR -> ValueType.StringValue()
			PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.BYTE, PrimitiveKind.SHORT -> ValueType.IntegerValue()
			PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> ValueType.NumberValue()
			PrimitiveKind.BOOLEAN -> ValueType.BooleanValue
			is StructureKind.LIST -> ValueType.ArrayValue(getElementDescriptor(0).toValueType())
			is StructureKind.CLASS, is StructureKind.OBJECT, is PolymorphicKind.SEALED -> ValueType.ObjectValue(
				(0 until elementsCount).associate { i ->
					getElementName(i).toSnakeCase() to getElementDescriptor(i).toValueType()
				}
			)
			
			is StructureKind.MAP -> ValueType.ObjectValue(emptyMap())
			SerialKind.ENUM -> ValueType.StringValue(
				(0 until elementsCount).map { getElementName(it) }
			)
			
			else -> error("Unsupported kind: $kind")
		}
		
		//从kotlinx.serialization.json抄的
		@Suppress("SameParameterValue")
		private fun convertCamelCase(
			serialName: String,
			delimiter: Char
		) = buildString(serialName.length * 2) {
			var bufferedChar: Char? = null
			var previousUpperCharsCount = 0
			
			serialName.forEach { c ->
				if (c.isUpperCase()) {
					if (previousUpperCharsCount == 0 && isNotEmpty() && last() != delimiter)
						append(delimiter)
					
					bufferedChar?.let(::append)
					
					previousUpperCharsCount++
					bufferedChar = c.lowercaseChar()
				} else {
					if (bufferedChar != null) {
						if (previousUpperCharsCount > 1 && c.isLetter()) {
							append(delimiter)
						}
						append(bufferedChar)
						previousUpperCharsCount = 0
						bufferedChar = null
					}
					append(c)
				}
			}
			
			if (bufferedChar != null) {
				append(bufferedChar)
			}
		}
	}
}
