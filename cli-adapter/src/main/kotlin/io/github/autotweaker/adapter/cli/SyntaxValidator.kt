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

package io.github.autotweaker.adapter.cli

object SyntaxValidator {
	fun collectParams(syntax: Syntax): List<Param> = when (syntax) {
		is Syntax.All -> syntax.children.flatMap { collectParams(it) }
		is Syntax.Xor -> syntax.children.flatMap { collectParams(it) }
		is Syntax.Leaf -> listOf(syntax.param)
	}
	
	fun checkConflicts(syntax: Syntax): List<String> {
		val params = collectParams(syntax).filter { it !is Param.Positional }
		val seen = mutableMapOf<String, String>()
		val conflicts = mutableListOf<String>()
		for (p in params) {
			seen[p.name]?.let { conflicts.add("Duplicate param name: ${p.name}") }
			seen[p.name] = p.name
			for (alias in p.aliases) {
				seen[alias]?.let { other ->
					conflicts.add("Alias conflict: '$alias' used by both --${p.name} and --$other")
				}
				seen[alias] = p.name
			}
		}
		return conflicts
	}
	
	fun countRequiredPositional(syntax: Syntax): Int = when (syntax) {
		is Syntax.All -> if (!syntax.required) 0 else syntax.children.sumOf { countRequiredPositional(it) }
		is Syntax.Xor -> 0
		is Syntax.Leaf -> if (syntax.required && syntax.param is Param.Positional) 1 else 0
	}
	
	fun validate(
		syntax: Syntax, activeValues: Set<String>, positionalCount: Int,
	): Boolean = when (syntax) {
		is Syntax.All -> {
			val hasPos = positionalCount > 0
			val anyActive = syntax.children.any { isActive(it, activeValues, hasPos) }
			val hasRequired = syntax.children.any { it.required }
			val requiredPos =
				syntax.children.sumOf { if (it.required && it is Syntax.Leaf && it.param is Param.Positional) 1 else 0 }
			if (syntax.required && hasRequired && !anyActive) false
			else if (positionalCount < requiredPos) false
			else syntax.children.all {
				val childPos = if (hasPositionalParam(it)) positionalCount else 0
				validate(it, activeValues, childPos)
			}
		}
		
		is Syntax.Xor -> {
			val hasPos = positionalCount > 0
			val byParam = syntax.children.count { isActive(it, activeValues, hasPositional = false) }
			val effectiveHasPos = byParam == 0 && hasPos
			val count =
				if (effectiveHasPos) syntax.children.count { isActive(it, activeValues, hasPositional = true) }
				else byParam
			when {
				syntax.required && count != 1 -> false
				count > 1 -> false
				count == 0 -> true
				else -> {
					val activeChild = syntax.children.first { isActive(it, activeValues, effectiveHasPos) }
					if (!effectiveHasPos && positionalCount > 0 && !hasPositionalParam(activeChild)) false
					else validate(activeChild, activeValues, positionalCount)
				}
			}
		}
		
		is Syntax.Leaf -> {
			if (syntax.param is Param.Positional) !(syntax.required && positionalCount < 1)
			else !(syntax.required && syntax.param.name !in activeValues)
		}
	}
	
	private fun isActive(
		syntax: Syntax, activeValues: Set<String>, hasPositional: Boolean = false,
	): Boolean = when (syntax) {
		is Syntax.All -> syntax.children.any { isActive(it, activeValues, hasPositional) }
		is Syntax.Xor -> {
			val anyByParam = syntax.children.any { isActive(it, activeValues, hasPositional = false) }
			anyByParam || (hasPositional && syntax.children.any {
				isActive(it, activeValues, hasPositional = true)
			})
		}
		
		is Syntax.Leaf -> {
			if (syntax.param is Param.Positional) hasPositional
			else syntax.param.name in activeValues
		}
	}
	
	private fun hasPositionalParam(syntax: Syntax): Boolean = when (syntax) {
		is Syntax.All -> syntax.children.any { hasPositionalParam(it) }
		is Syntax.Xor -> syntax.children.any { hasPositionalParam(it) }
		is Syntax.Leaf -> syntax.param is Param.Positional
	}
}
