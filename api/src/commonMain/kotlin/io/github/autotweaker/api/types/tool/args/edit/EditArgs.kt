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

package io.github.autotweaker.api.types.tool.args.edit

import io.github.autotweaker.api.tool.ToolArgs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
sealed class EditArgs : ToolArgs<EditArgs> {
	override fun serializer(): KSerializer<EditArgs> = Companion.serializer()
	
	@Serializable
	data class Run(
		val files: List<String>,
		val actions: List<EditAction>,
		val dryRun: Boolean = false,
	) : EditArgs()
	
	@Serializable
	data class Apply(val operationId: String) : EditArgs()
	
	@Serializable
	data class GetClip(val clipId: String) : EditArgs()
}
