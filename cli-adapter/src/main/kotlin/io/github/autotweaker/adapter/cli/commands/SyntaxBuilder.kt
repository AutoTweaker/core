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

package io.github.autotweaker.adapter.cli.commands

import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef

inline fun buildSyntax(block: SyntaxBuilder.() -> Unit) = SyntaxBuilder().apply(block)

@Suppress("unused")
inline fun buildSyntax(xor: XOR, block: SyntaxBuilder.() -> Unit) = buildSyntax(block).toXor()

@Suppress("unused")
inline fun buildSyntax(all: ALL, block: SyntaxBuilder.() -> Unit) = buildSyntax(block).toAll()

object XOR; object ALL

@SyntaxDsl
class SyntaxBuilder : I18nable {
	var required: Boolean = true
	private val syntax = mutableListOf<Syntax>()
	
	fun all(block: SyntaxBuilder.() -> Unit) {
		syntax.add(buildSyntax(ALL, block))
	}
	
	fun xor(block: SyntaxBuilder.() -> Unit) {
		syntax.add(buildSyntax(XOR, block))
	}
	
	
	fun flag(name: String, desc: I18nDef, block: SyntaxLeafBuilder.() -> Unit = {}) {
		leaf(name, i18n(desc), block) { toFlag() }
	}
	
	fun flag(name: String, desc: String, block: SyntaxLeafBuilder.() -> Unit = {}) {
		leaf(name, desc, block) { toFlag() }
	}
	
	fun value(name: String, desc: I18nDef, block: SyntaxLeafBuilder.() -> Unit = {}) {
		leaf(name, i18n(desc), block) { toValue() }
	}
	
	fun value(name: String, desc: String, block: SyntaxLeafBuilder.() -> Unit = {}) {
		leaf(name, desc, block) { toValue() }
	}
	
	fun positional(name: String, desc: I18nDef, block: SyntaxLeafBuilder.() -> Unit = {}) {
		leaf(name, i18n(desc), block) { toPositional() }
	}
	
	fun positional(name: String, desc: String, block: SyntaxLeafBuilder.() -> Unit = {}) {
		leaf(name, desc, block) { toPositional() }
	}
	
	
	private fun leaf(
		name: String,
		desc: String,
		block: SyntaxLeafBuilder.() -> Unit,
		transform: SyntaxLeafBuilder.() -> Syntax.Leaf
	) {
		syntax.add(
			SyntaxLeafBuilder(name, desc).apply(block).transform()
		)
	}
	
	fun toAll() = Syntax.All(syntax, required)
	fun toXor() = Syntax.Xor(syntax, required)
}

@SyntaxDsl
class SyntaxLeafBuilder(
	private val name: String,
	private val desc: String
) : I18nable {
	var required = true
	private var aliases: Set<String>? = null
	
	fun aliases(vararg aliases: String) {
		this.aliases = this.aliases.orEmpty() + aliases.toSet()
	}
	
	fun toFlag() = Syntax.Leaf(Param.Flag(name, desc, buildAliases()), required)
	fun toValue() = Syntax.Leaf(Param.Value(name, desc, buildAliases()), required)
	fun toPositional() = Syntax.Leaf(Param.Positional(name, desc), required)
	
	private fun buildAliases() =
		aliases?.toList()
			?: if (name.length > 1)
				listOf(name[0].toString())
			else emptyList()
}


@DslMarker
annotation class SyntaxDsl
