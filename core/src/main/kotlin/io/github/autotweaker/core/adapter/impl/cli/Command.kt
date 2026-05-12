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
import kotlinx.coroutines.flow.Flow

interface Command {
	val name: String
	val description: String
	val params: List<Param>
	
	fun init(core: CoreAPI, coreVersion: SemVer) {}
	fun handle(request: ParsedRequest, prompt: suspend (String) -> String): Flow<Chunk>
	
	data class Param(
		val name: String,
		val description: String,
		val required: Boolean = false,
		val type: ParamType = ParamType.POSITIONAL,
	)
	
	enum class ParamType {
		FLAG_LONG, FLAG_SHORT, VALUE_LONG, VALUE_SHORT, POSITIONAL, ;
		
		fun format(name: String): String = when (this) {
			FLAG_LONG -> "--$name"
			FLAG_SHORT -> "-$name"
			VALUE_LONG -> "--$name <value>"
			VALUE_SHORT -> "-$name <value>"
			POSITIONAL -> "<$name>"
		}
	}
	
	sealed class Chunk {
		data class Data(val text: String, val channel: Channel = Channel.STDOUT, val newline: Boolean = true) : Chunk()
		data class Done(val exitCode: Int = 0) : Chunk()
		
		enum class Channel { STDOUT, STDERR }
	}
}