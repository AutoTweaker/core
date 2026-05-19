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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class CliMessage {
	@Serializable
	@SerialName("cmd")
	data class Command(
		val args: List<String> = emptyList(),
		val prog: String = "autotweaker",
	) : CliMessage() {
		fun command(): String = args.firstOrNull() ?: ""
		
		@Suppress("unused")
		fun arg(index: Int): String? = args.getOrNull(index)
		
		fun option(long: String, short: String): String? {
			val idx = args.indexOf(long).let { if (it >= 0) it else args.indexOf(short) }
			return if (idx >= 0) args.getOrNull(idx + 1) else null
		}
		
		fun flag(name: String): Boolean = name in args
	}
	
	@Serializable
	@SerialName("reply")
	data class PromptResponse(val text: String) : CliMessage()
}
