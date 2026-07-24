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

@file:Suppress("unused")

package io.github.autotweaker.toolgen

import java.nio.file.Path

fun tool(name: String, block: ToolMetaBuilder.() -> Unit) =
	ToolMetaBuilder(name).apply(block).toMeta()

fun ToolMeta.gen(argsPackage: String, toolPackage: String) {
	val dir = Path.of(System.getProperty("toolgen.outputDir", "build/generated/args"))
	ArgsCodeGen(this, argsPackage, toolPackage)(dir)
}

@ToolMetaDsl
class ToolMetaBuilder internal constructor(
	private val name: String,
) {
	init {
		require('-' !in name) { "Name '$name' must not contain '-'" }
	}
	
	private val functions = mutableListOf<ToolMeta.Function>()
	private val declaration = mutableListOf<ToolMeta.Type.Declared>()
	
	fun function(name: String, block: FunctionBuilder.() -> Unit) {
		require('-' !in name) { "Name '$name' must not contain '-'" }
		functions.add(FunctionBuilder(name).apply(block).toFunction())
	}
	
	fun buildDeclaration(block: DeclarationBuilder.() -> Unit) =
		DeclarationBuilder(declaration).apply(block).declaration()
	
	internal fun toMeta() = ToolMeta(
		name = name,
		functions = functions,
		declared = declaration
	)
}

@ToolMetaDsl
class FunctionBuilder internal constructor(
	private val name: String,
) : ParametersBuilder() {
	internal fun toFunction() = ToolMeta.Function(
		name = name,
		parameters = parameters
	)
}


@ToolMetaDsl
class DeclarationBuilder internal constructor(
	private val registry: MutableList<ToolMeta.Type.Declared>
) {
	private lateinit var declaration: ToolMeta.Type.Declared
	
	internal fun declaration() = declaration.also {
		if (it is ToolMeta.Type.Obj || it is ToolMeta.Type.Enum || it is ToolMeta.Type.OneOf)
			registry.add(it)
	}
	
	fun obj(name: String, block: ParametersBuilder.() -> Unit) {
		val parameters = ParametersBuilder().apply(block).parameters
		declaration = ToolMeta.Type.Obj(name, parameters)
	}
	
	fun enum(name: String, vararg values: String) {
		declaration = ToolMeta.Type.Enum(name, values.toSet())
	}
	
	fun oneOf(name: String, block: VariantsBuilder.() -> Unit) {
		val variants = VariantsBuilder().apply(block).variants
		declaration = ToolMeta.Type.OneOf(name, variants)
	}
	
	fun list(element: ToolMeta.Type.Declared) {
		declaration = ToolMeta.Type.TList(element)
	}
	
	fun map(element: ToolMeta.Type.Declared) {
		declaration = ToolMeta.Type.TMap(element)
	}
}

@ToolMetaDsl
class VariantsBuilder internal constructor() {
	internal val variants = mutableListOf<ToolMeta.Type.OneOf.Variant>()
	
	fun variant(name: String, block: ParametersBuilder.() -> Unit = {}) {
		val parameters = ParametersBuilder().apply(block).parameters
		variants.add(ToolMeta.Type.OneOf.Variant(name, parameters))
	}
}

@ToolMetaDsl
open class ParametersBuilder internal constructor() {
	internal val parameters = mutableListOf<ToolMeta.Prop>()
	
	fun param(name: String, type: ToolMeta.Type.Declared, block: PropBuilder.() -> Unit = {}) {
		prop(name, type, block)
	}
	
	
	fun string(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TString, block)
	}
	
	fun int(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TInt, block)
	}
	
	fun long(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TLong, block)
	}
	
	fun double(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TDouble, block)
	}
	
	fun boolean(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TBoolean, block)
	}
	
	
	fun stringList(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TList(ToolMeta.Type.TString), block)
	}
	
	fun intList(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TList(ToolMeta.Type.TInt), block)
	}
	
	fun longList(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TList(ToolMeta.Type.TLong), block)
	}
	
	fun doubleList(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TList(ToolMeta.Type.TDouble), block)
	}
	
	fun booleanList(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TList(ToolMeta.Type.TBoolean), block)
	}
	
	
	fun stringMap(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TMap(ToolMeta.Type.TString), block)
	}
	
	fun intMap(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TMap(ToolMeta.Type.TInt), block)
	}
	
	fun longMap(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TMap(ToolMeta.Type.TLong), block)
	}
	
	fun doubleMap(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TMap(ToolMeta.Type.TDouble), block)
	}
	
	fun booleanMap(name: String, block: PropBuilder.() -> Unit = {}) {
		prop(name, ToolMeta.Type.TMap(ToolMeta.Type.TBoolean), block)
	}
	
	
	private fun prop(name: String, type: ToolMeta.Type, block: PropBuilder.() -> Unit) {
		require(name != "type") { "Property name 'type' conflicts with the sealed class discriminator" }
		parameters.add(PropBuilder(name, type).apply(block).toProp())
	}
}

@ToolMetaDsl
class PropBuilder internal constructor(
	private val name: String,
	private val type: ToolMeta.Type,
) {
	var required = true
	
	internal fun toProp() = ToolMeta.Prop(
		name = name,
		type = type,
		required = required,
	)
}

@DslMarker
internal annotation class ToolMetaDsl
