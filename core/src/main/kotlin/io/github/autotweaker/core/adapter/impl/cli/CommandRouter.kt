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
import io.github.autotweaker.core.adapter.impl.cli.Command.Param
import io.github.autotweaker.core.adapter.impl.cli.Command.ParamType
import io.github.autotweaker.core.adapter.impl.cli.commands.Help
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.*

class CommandRouter(core: CoreAPI, coreVersion: SemVer) {
	private val handlers: Map<String, Command>
	
	init {
		val loaded = ServiceLoader.load(Command::class.java).toList()
		loaded.forEach { it.init(core, coreVersion) }
		val help = Help(loaded)
		handlers = (loaded + help).associateBy { it.name }
	}
	
	fun dispatch(request: Request, prompt: suspend (String) -> String): Flow<Command.Chunk> {
		val cmd = request.command()
		val handler =
			handlers[cmd] ?: return flowOf(Command.Chunk.Data("Unknown command: $cmd. Run 'autotweaker help'.\n"))
		
		val parsed = parse(request, handler.params) ?: run {
			return flowOf(Command.Chunk.Data("Invalid arguments. Run 'autotweaker help $cmd' for usage.\n"))
		}
		
		return handler.handle(parsed, prompt)
	}
	
	private fun parse(request: Request, params: List<Param>): ParsedRequest? {
		val positional = mutableListOf<String>()
		val values = mutableMapOf<String, String>()
		val longParams = params.filter { it.type == ParamType.FLAG_LONG || it.type == ParamType.VALUE_LONG }
		val shortParams = params.filter { it.type == ParamType.FLAG_SHORT || it.type == ParamType.VALUE_SHORT }
		val args = request.args.drop(1)
		
		var i = 0
		while (i < args.size) {
			val arg = args[i]
			when {
				arg.startsWith("--") -> {
					val eq = arg.indexOf('=')
					val key = if (eq >= 0) arg.substring(2, eq) else arg.substring(2)
					val p = longParams.find { it.name == key } ?: return null
					when (p.type) {
						ParamType.FLAG_LONG -> values[key] = "true"
						ParamType.VALUE_LONG -> values[key] =
							if (eq >= 0) arg.substring(eq + 1) else args.getOrNull(++i) ?: return null
						
						else -> return null
					}
					i++
				}
				
				arg.startsWith("-") && arg.length > 1 && arg[1] != '-' -> {
					val eq = arg.indexOf('=')
					val tail = arg.substring(1)
					val key = if (eq >= 0) tail.substring(0, eq - 1) else tail
					val valParam = shortParams.find { it.name == key && it.type == ParamType.VALUE_SHORT }
					if (valParam != null) {
						if (eq >= 0) {
							values[key] = arg.substring(eq + 1)
						} else {
							values[key] = args.getOrNull(++i) ?: return null
						}
					} else {
						for (c in tail) {
							val fp = shortParams.find { it.name == c.toString() && it.type == ParamType.FLAG_SHORT }
								?: return null
							values[fp.name] = "true"
						}
					}
					i++
				}
				
				else -> {
					positional.add(arg)
					i++
				}
			}
		}
		
		val declaredPosCount = params.count { it.type == ParamType.POSITIONAL }
		val requiredPosCount = params.count { it.type == ParamType.POSITIONAL && it.required }
		if (positional.size !in requiredPosCount..declaredPosCount) return null
		
		for (p in params.filter { it.required && it.type != ParamType.POSITIONAL }) {
			if (p.name !in values) return null
		}
		
		return ParsedRequest(request.stdin, values, positional)
	}
}