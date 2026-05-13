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

package io.github.autotweaker.core.adapter.impl.cli

import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.cli.Command.Chunk
import io.github.autotweaker.core.adapter.impl.cli.commands.Help
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.slf4j.LoggerFactory
import java.util.*

class CommandRouter(core: CoreAPI, coreVersion: SemVer) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val handlers: Map<String, Command>
	
	init {
		val loaded = ServiceLoader.load(Command::class.java).toList()
		loaded.forEach { it.init(core, coreVersion) }
		val help = Help(loaded)
		handlers = (loaded + help).associateBy { it.name }
		logger.debug("CommandRouter loaded  commandCount={}  commands={}", handlers.size, handlers.keys)
	}
	
	fun dispatch(request: CliMessage.Command, prompt: suspend (String) -> String): Flow<Chunk> {
		val cmd = request.command()
		if (cmd.isEmpty()) {
			return flowOf(
				Chunk.Data("AutoTweaker  Copyright (C) 2026  WhiteElephant-abc"), Chunk.Done()
			)
		}
		val handler = handlers[cmd]
		if (handler == null) {
			logger.warn("Unknown command received  command={}  args={}", cmd, request.args)
			return flowOf(
				Chunk.Data(I18n.get("cmd.unknown_hint", cmd, request.prog), Chunk.Channel.STDERR),
				Chunk.Done(1),
			)
		}
		
		logger.debug("Command dispatched  command={}  args={}", cmd, request.args.drop(1))
		val parsed = parse(request, handler.syntax)
		if (parsed == null) {
			logger.debug("Invalid arguments for command  command={}", cmd)
			return flowOf(
				Chunk.Data(I18n.get("cmd.invalid_args", cmd, request.prog), Chunk.Channel.STDERR),
				Chunk.Done(1),
			)
		}
		
		return handler.handle(parsed, prompt)
	}
	
	private fun parse(request: CliMessage.Command, syntax: Syntax): Request? {
		val positional = mutableListOf<String>()
		val values = mutableMapOf<String, String>()
		val allParams = collectParams(syntax).distinctBy { it.name }
		val aliasMap = buildAliasMap(allParams)
		val args = request.args.drop(1)
		
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
								values[key] = args.getOrNull(++i) ?: return null
								i++
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
							values[valKey] = args.getOrNull(i + 1) ?: return null
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
		val requiredPosCount = countRequiredPositionals(syntax)
		if (positional.size !in requiredPosCount..declaredPosCount) return null
		
		if (!validateSyntax(syntax, values.keys, positional.isNotEmpty())) {
			logger.debug("Syntax validation failed")
			return null
		}
		
		return Request(values, positional, request.prog, aliasMap)
	}
	
	private fun collectParams(syntax: Syntax): List<Param> = when (syntax) {
		is Syntax.All -> syntax.children.flatMap { collectParams(it) }
		is Syntax.Xor -> syntax.children.flatMap { collectParams(it) }
		is Syntax.Leaf -> listOf(syntax.param)
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
	
	private fun countRequiredPositionals(syntax: Syntax): Int = when (syntax) {
		is Syntax.All -> if (!syntax.required) 0 else syntax.children.sumOf { countRequiredPositionals(it) }
		is Syntax.Xor -> if (!syntax.required) 0 else syntax.children.minOf { countRequiredPositionals(it) }
		is Syntax.Leaf -> if (syntax.required && syntax.param is Param.Positional) 1 else 0
	}
	
	private fun validateSyntax(syntax: Syntax, activeValues: Set<String>, hasPositional: Boolean = false): Boolean =
		when (syntax) {
			is Syntax.All -> {
				val anyActive = syntax.children.any { isActive(it, activeValues, hasPositional) }
				if (syntax.required && !anyActive) false
				else if (!anyActive) true
				else syntax.children.all { validateSyntax(it, activeValues, hasPositional) }
			}
			
			is Syntax.Xor -> {
				val count = syntax.children.count { isActive(it, activeValues, hasPositional) }
				when {
					syntax.required && count != 1 -> false
					count > 1 -> false
					count == 0 -> true
					else -> validateSyntax(
						syntax.children.first { isActive(it, activeValues, hasPositional) },
						activeValues,
						hasPositional,
					)
				}
			}
			
			is Syntax.Leaf -> {
				!(syntax.required && syntax.param !is Param.Positional && syntax.param.name !in activeValues)
			}
		}
	
	private fun isActive(syntax: Syntax, activeValues: Set<String>, hasPositional: Boolean = false): Boolean =
		when (syntax) {
			is Syntax.All -> syntax.children.any { isActive(it, activeValues, hasPositional) }
			is Syntax.Xor -> syntax.children.any { isActive(it, activeValues, hasPositional) }
			is Syntax.Leaf -> {
				if (syntax.param is Param.Positional) hasPositional
				else syntax.param.name in activeValues
			}
		}
}
