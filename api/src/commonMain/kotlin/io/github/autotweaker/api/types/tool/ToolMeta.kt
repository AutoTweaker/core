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

package io.github.autotweaker.api.types.tool

data class ToolMeta(
	val name: String,
	val description: String,
	val functions: List<Function>,
) {
	data class Function(
		val name: String,
		val description: String,
		val parameters: List<Prop>,
	)
	
	data class Prop(
		val name: String,
		val type: Type,
		val required: Boolean,
		val description: String,
	)
	
	sealed interface Type {
		data class OneOf(
			val name: String,
			val variants: List<Variant>,
		) : Type {
			data class Variant(
				val name: String,
				val description: String,
				val properties: List<Prop>,
			)
		}
		
		data class Obj(
			val name: String,
			val properties: List<Prop>,
		) : Type
		
		data class Enum(
			val name: String,
			val values: Set<String>,
		) : Type
		
		data class TList(val element: Type) : Type
		
		data class TMap(val element: Type) : Type
		
		data object TString : Type
		data object TInt : Type
		data object TLong : Type
		data object TDouble : Type
		data object TBoolean : Type
	}
}
