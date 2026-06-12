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

import org.slf4j.LoggerFactory

class ArgParser(
	private val maxArgsCount: Int,
) {
	private val syntaxValidator = SyntaxValidator
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	fun parse(args: List<String>, syntax: Syntax, prog: String): Request? {
		val positional = mutableListOf<String>()
		val values = mutableMapOf<String, String>()
		var posCounter = 0
		val allParams = syntaxValidator.collectParams(syntax).map {
			if (it is Param.Positional) it.copy(name = $$"$pos_$${posCounter++}") else it
		}.let {
			it.filterNot { param -> param is Param.Positional }
				.distinctBy { param -> param.name } + it.filterIsInstance<Param.Positional>()
		}
		val aliasMap = buildAliasMap(allParams)
		if (args.size > maxArgsCount) return null
		
		var i = 0
		while (i < args.size) {
			val arg = args[i]
			when {
				arg == "--" -> {
					positional.addAll(args.subList(i + 1, args.size))
					break
				}
				
				arg.startsWith("--") -> {
					val eq = arg.indexOf('=')
					val rawKey = if (eq >= 0) arg.substring(2, eq) else arg.substring(2)
					val key = aliasMap[rawKey] ?: rawKey
					val p = allParams.find { it.name == key } ?: return null
					when (p) {
						is Param.Flag -> {
							if (eq >= 0) return null
							values[key] = "true"
							i++
						}
						
						is Param.Value -> {
							if (eq >= 0) {
								values[key] = arg.substring(eq + 1)
								i++
							} else {
								val next = args.getOrNull(i + 1) ?: return null
								if (next == "--") return null
								values[key] = next
								i += 2
							}
						}
						
						is Param.Positional -> return null
					}
				}
				
				arg.startsWith("-") && arg.length > 1 && arg[1] != '-' -> {
					val eq = arg.indexOf('=')
					val tail = arg.substring(1)
					
					if (eq >= 0) {
						val rawKey = tail.substringBefore('=')
						val key = aliasMap[rawKey] ?: rawKey
						val p = allParams.find { it.name == key } ?: return null
						if (p !is Param.Value) return null
						values[key] = arg.substring(eq + 1)
						i++
					} else {
						val valKey = aliasMap[tail] ?: tail
						val valParam = allParams.find { it.name == valKey && it is Param.Value }
						if (valParam != null) {
							val next = args.getOrNull(i + 1) ?: return null
							if (next == "--") return null
							values[valKey] = next
							i += 2
						} else {
							for (c in tail) {
								val flagKey = aliasMap[c.toString()] ?: c.toString()
								allParams.find { it.name == flagKey && it is Param.Flag } ?: return null
								values[flagKey] = "true"
							}
							i++
						}
					}
				}
				
				else -> {
					positional.add(arg)
					i++
				}
			}
		}
		
		val declaredPosCount = allParams.count { it is Param.Positional }
		val requiredPosCount = syntaxValidator.countRequiredPositional(syntax)
		if (positional.size !in requiredPosCount..declaredPosCount) return null
		
		if (!syntaxValidator.validate(syntax, values.keys, positional.size)) {
			logger.debug("Failed syntax validation")
			return null
		}
		
		return Request(values, positional, prog, aliasMap)
	}
	
	private fun buildAliasMap(params: List<Param>): Map<String, String> {
		val map = mutableMapOf<String, String>()
		for (p in params) {
			for (alias in p.aliases) {
				map[alias] = p.name
			}
		}
		return map
	}
}
