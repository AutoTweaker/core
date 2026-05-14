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
	val syntax: Syntax
	
	fun init(core: CoreAPI, coreVersion: SemVer) {}
	fun handle(request: Request, prompt: suspend (text: String, echo: Boolean) -> String): Flow<Chunk>
	
	sealed class Chunk {
		data class Data(val text: String, val channel: Channel = Channel.STDOUT, val newline: Boolean = true) : Chunk()
		data class Done(val exitCode: Int = 0) : Chunk()
		
		enum class Channel { STDOUT, STDERR }
	}
}
