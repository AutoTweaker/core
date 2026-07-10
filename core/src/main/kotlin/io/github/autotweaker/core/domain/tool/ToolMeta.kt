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

import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.trace
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.JsonClassDiscriminator
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
		data class ObjectValue(val properties: Map<String, Property>) : ValueType()
		data class MapValue(val key: ValueType, val value: ValueType) : ValueType()
		data object AnyValue : ValueType()
		data class OneOfValue(val variants: Map<String, ValueType>) : ValueType()
	}
	
	data class SealedPath(
		val segments: List<String>,
		val typeMap: Map<String, String>,
	)
	
	companion object : Traceable {
		fun String.toSnakeCase() = convertCamelCase(this, '_')
		
		private fun buildDescIndex(
			describeMap: Map<KProperty1<*, *>, String>
		): Map<String, String> = describeMap.entries.associate { (prop, desc) ->
			val className = prop.javaField?.declaringClass?.name?.replace('$', '.')
				?: error("Property '${prop.name}' has no backing field")
			"${className}.${prop.name}" to desc
		}
		
		@OptIn(ExperimentalSerializationApi::class)
		suspend fun build(tool: Tool<ToolArgs>): ToolMeta {
			require('-' !in tool.name) { "Tool name must not contain '-': ${tool.name}" }
			val desc = tool.argsSerializer.descriptor
			val describeMap = tool.describe()
			val descIndex = buildDescIndex(describeMap)
			return if (desc.kind == PolymorphicKind.SEALED) {
				buildSealed(tool, desc, describeMap, tool.describeFunctions(), descIndex)
			} else {
				buildSingle(tool, desc, describeMap, descIndex)
			}
		}
		
		private fun buildSingle(
			tool: Tool<ToolArgs>,
			desc: SerialDescriptor,
			describeMap: Map<KProperty1<*, *>, String>,
			descIndex: Map<String, String>,
		): ToolMeta {
			val descByName = describeMap.entries.associate { (prop, d) -> prop.name to d }
			val params = (0 until desc.elementsCount).associate { i ->
				val fieldName = desc.getElementName(i)
				fieldName.toSnakeCase() to Property(
					description = descByName[fieldName] ?: error("Missing description for '$fieldName'"),
					required = !desc.isElementOptional(i),
					valueType = desc.getElementDescriptor(i).toValueType(descIndex),
				)
			}
			return ToolMeta(tool.name, tool.description, listOf(Function("run", tool.description, params)))
		}
		
		@OptIn(ExperimentalSerializationApi::class)
		private fun buildSealed(
			tool: Tool<ToolArgs>,
			desc: SerialDescriptor,
			describeMap: Map<KProperty1<*, *>, String>,
			funcDescMap: Map<KClass<*>, String>,
			descIndex: Map<String, String>,
		): ToolMeta {
			val sealedBaseClass = funcDescMap.keys.firstOrNull()?.java?.enclosingClass?.kotlin
			sealedBaseClass?.annotations?.filterIsInstance<JsonClassDiscriminator>()?.firstOrNull()?.let { annotation ->
				require(annotation.discriminator == "type") {
					"Sealed base class must not use custom discriminator: ${sealedBaseClass.qualifiedName}"
				}
			}
			
			for ((prop, _) in describeMap) {
				require(prop.annotations.filterIsInstance<SerialName>().isEmpty()) {
					"Parameter must not use @SerialName: ${prop.name}"
				}
			}
			
			val grouped = describeMap.entries.groupBy { (prop, _) ->
				prop.javaField?.declaringClass
					?: error("Property '${prop.name}' has no backing field (delegated/synthetic)")
			}
			
			val sealedDesc = desc.getElementDescriptor(1)
			val functions = (0 until sealedDesc.elementsCount).map { i ->
				val subDesc = sealedDesc.getElementDescriptor(i)
				val descName = sealedDesc.getElementName(i)
				val funcClass = funcDescMap.keys.firstOrNull { it.qualifiedName == descName }
					?: error("Sealed subclass '$descName' not found in describeFunctions()")
				val funcName = funcClass.simpleName ?: error("Anonymous sealed subclass '$descName'")
				require('-' !in funcName) { "Function name must not contain '-': $funcName" }
				require(funcClass.annotations.filterIsInstance<SerialName>().isEmpty()) {
					"Sealed subclass must not use @SerialName: ${funcClass.qualifiedName}"
				}
				val funcEntries = grouped[funcClass.java].orEmpty()
				val descByName = funcEntries.associate { (prop, d) -> prop.name to d }
				val funcDesc = funcDescMap[funcClass]
					?: error("Missing function description for '$funcName' in describeFunctions()")
				
				val params = (0 until subDesc.elementsCount).associate { j ->
					val fieldName = subDesc.getElementName(j)
					fieldName.toSnakeCase() to Property(
						description = descByName[fieldName]
							?: error("Missing description for '$fieldName' in '$funcName'"),
						required = !subDesc.isElementOptional(j),
						valueType = subDesc.getElementDescriptor(j).toValueType(descIndex),
					)
				}
				Function(funcName.toSnakeCase(), funcDesc, params)
			}
			return ToolMeta(tool.name, tool.description, functions)
		}
		
		@OptIn(ExperimentalSerializationApi::class)
		private fun SerialDescriptor.toValueType(descIndex: Map<String, String>): ValueType = when (serialName) {
			"kotlinx.serialization.json.JsonElement",
			"kotlinx.serialization.json.JsonObject",
			"kotlinx.serialization.json.JsonPrimitive" -> ValueType.AnyValue
			
			else -> when (kind) {
				PrimitiveKind.STRING, PrimitiveKind.CHAR -> ValueType.StringValue()
				PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.BYTE, PrimitiveKind.SHORT -> ValueType.IntegerValue()
				PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> ValueType.NumberValue()
				PrimitiveKind.BOOLEAN -> ValueType.BooleanValue
				is StructureKind.LIST -> ValueType.ArrayValue(getElementDescriptor(0).toValueType(descIndex))
				is StructureKind.CLASS, is StructureKind.OBJECT -> ValueType.ObjectValue(
					(0 until elementsCount).associate { i ->
						val fieldName = getElementName(i)
						val key = "${serialName.removeSuffix("?")}.${fieldName}"
						fieldName.toSnakeCase() to Property(
							description = descIndex[key]
								?: error("Missing description for '$key'"),
							required = !isElementOptional(i),
							valueType = getElementDescriptor(i).toValueType(descIndex),
						)
					}
				)
				
				is PolymorphicKind.SEALED -> {
					val dispatchDesc = getElementDescriptor(1)
					for (i in 0 until dispatchDesc.elementsCount) {
						val subName = dispatchDesc.getElementName(i)
						require(tryLoadClass(subName) != null) {
							"Sealed subclass not found (may use @SerialName): $subName"
						}
					}
					val variants = (0 until dispatchDesc.elementsCount).associate { i ->
						val variantName = dispatchDesc.getElementName(i).substringAfterLast('.').toSnakeCase()
						val variantDesc = dispatchDesc.getElementDescriptor(i)
						val properties = (0 until variantDesc.elementsCount).associate { j ->
							val fieldName = variantDesc.getElementName(j)
							val key = "${variantDesc.serialName.removeSuffix("?")}.${fieldName}"
							fieldName.toSnakeCase() to Property(
								description = descIndex[key]
									?: error("Missing description for '$key'"),
								required = !variantDesc.isElementOptional(j),
								valueType = variantDesc.getElementDescriptor(j).toValueType(descIndex),
							)
						}
						variantName to ValueType.ObjectValue(properties)
					}
					ValueType.OneOfValue(variants)
				}
				
				is StructureKind.MAP -> ValueType.MapValue(
					getElementDescriptor(0).toValueType(descIndex),
					getElementDescriptor(1).toValueType(descIndex),
				)
				
				SerialKind.ENUM -> ValueType.StringValue(
					(0 until elementsCount).map { getElementName(it) }
				)
				
				else -> error("Unsupported kind: $kind")
			}
		}
		
		@OptIn(ExperimentalSerializationApi::class)
		fun buildTypeMapping(tool: Tool<ToolArgs>): List<SealedPath> {
			val desc = tool.argsSerializer.descriptor
			val result = mutableListOf<SealedPath>()
			if (desc.kind == PolymorphicKind.SEALED) {
				val sealedDesc = desc.getElementDescriptor(1)
				for (i in 0 until sealedDesc.elementsCount) {
					val subDesc = sealedDesc.getElementDescriptor(i)
					for (j in 0 until subDesc.elementsCount) {
						collectSealedPaths(
							subDesc.getElementDescriptor(j),
							subDesc.getElementName(j).toSnakeCase(),
							result
						)
					}
				}
			} else {
				for (i in 0 until desc.elementsCount) {
					collectSealedPaths(desc.getElementDescriptor(i), desc.getElementName(i).toSnakeCase(), result)
				}
			}
			return result
		}
		
		@OptIn(ExperimentalSerializationApi::class)
		private fun collectSealedPaths(
			desc: SerialDescriptor,
			currentPath: String,
			result: MutableList<SealedPath>,
		) {
			when (desc.kind) {
				is PolymorphicKind.SEALED -> {
					val dispatchDesc = desc.getElementDescriptor(1)
					val typeMap = (0 until dispatchDesc.elementsCount).associate { i ->
						val name = dispatchDesc.getElementName(i)
						name.substringAfterLast('.').toSnakeCase() to name
					}
					result += SealedPath(currentPath.split('.'), typeMap)
					for (i in 0 until dispatchDesc.elementsCount) {
						val variantDesc = dispatchDesc.getElementDescriptor(i)
						for (j in 0 until variantDesc.elementsCount) {
							collectSealedPaths(
								variantDesc.getElementDescriptor(j),
								"$currentPath.${variantDesc.getElementName(j).toSnakeCase()}",
								result,
							)
						}
					}
				}
				
				is StructureKind.CLASS, is StructureKind.OBJECT -> {
					for (i in 0 until desc.elementsCount) {
						collectSealedPaths(
							desc.getElementDescriptor(i),
							"$currentPath.${desc.getElementName(i).toSnakeCase()}",
							result,
						)
					}
				}
				
				is StructureKind.LIST -> collectSealedPaths(desc.getElementDescriptor(0), currentPath, result)
				is StructureKind.MAP -> collectSealedPaths(desc.getElementDescriptor(1), currentPath, result)
				else -> {}
			}
		}
		
		private fun tryLoadClass(fqcn: String): Class<*>? {
			var name = fqcn
			while ('.' in name) {
				trace.catching { return Class.forName(name) }
				val lastDot = name.lastIndexOf('.')
				name = name.substring(0, lastDot) + '$' + name.substring(lastDot + 1)
			}
			return trace.catching { Class.forName(name) }.getOrNull()
		}
		
		// Adapted from kotlinx.serialization.json.JsonNamingStrategy
		// SPDX-License-Identifier: Apache-2.0
		// Copyright JetBrains s.r.o. and kotlinx.serialization contributors
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
