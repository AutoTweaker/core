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

package io.github.autotweaker.toolgen

@ConsistentCopyVisibility
data class ToolMeta internal constructor(
	val name: String,
	val functions: List<Function>,
	val declared: List<Type.Declared>
) {
	@ConsistentCopyVisibility
	data class Function internal constructor(
		val name: String,
		val parameters: List<Prop>,
	)
	
	@ConsistentCopyVisibility
	data class Prop internal constructor(
		val name: String,
		val type: Type,
		val required: Boolean,
	)
	
	sealed interface Type {
		sealed interface Builtin : Type
		sealed interface Declared : Type
		
		@ConsistentCopyVisibility
		data class OneOf internal constructor(
			val name: String,
			val variants: List<Variant>,
		) : Type, Declared {
			@ConsistentCopyVisibility
			data class Variant internal constructor(
				val name: String,
				val properties: List<Prop>,
			)
		}
		
		@ConsistentCopyVisibility
		data class Obj internal constructor(
			val name: String,
			val properties: List<Prop>,
		) : Type, Declared
		
		@ConsistentCopyVisibility
		data class Enum internal constructor(
			val name: String,
			val values: Set<String>,
		) : Type, Declared
		
		@ConsistentCopyVisibility
		data class TList internal constructor(val element: Type) : Type, Declared
		
		@ConsistentCopyVisibility
		data class TMap internal constructor(val element: Type) : Type, Declared
		
		data object TString : Type, Builtin
		data object TInt : Type, Builtin
		data object TLong : Type, Builtin
		data object TDouble : Type, Builtin
		data object TBoolean : Type, Builtin
	}
}
