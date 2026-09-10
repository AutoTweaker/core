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

package io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.tool

import io.github.autotweaker.api.types.serializer.PathSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
sealed interface V0UiBlock {
	@Serializable
	@SerialName("io.github.autotweaker.api.types.tool.UiBlock.Text")
	data class Text(val content: String) : V0UiBlock
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.tool.UiBlock.Command")
	data class Command(val command: String) : V0UiBlock
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.tool.UiBlock.Diff")
	data class Diff(
		@Serializable(with = PathSerializer::class)
		val filePath: Path,
		val oldContent: String?,
		val newContent: String,
	) : V0UiBlock
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.tool.UiBlock.Output")
	data class Output(val content: String) : V0UiBlock
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.tool.UiBlock.Error")
	data class Error(val content: String) : V0UiBlock
}
