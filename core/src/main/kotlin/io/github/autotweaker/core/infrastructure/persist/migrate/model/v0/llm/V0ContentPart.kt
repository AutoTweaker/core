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

package io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class V0ContentPart {
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.Text")
	data class Text(val content: String) : V0ContentPart()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.Image")
	data class Image(val mimeType: String, val data: V0Sha256) : V0ContentPart()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.ImageUrl")
	data class ImageUrl(val url: V0Url) : V0ContentPart()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.Audio")
	data class Audio(val mimeType: String, val data: V0Sha256) : V0ContentPart()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.AudioUrl")
	data class AudioUrl(val url: V0Url) : V0ContentPart()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.Video")
	data class Video(val mimeType: String, val data: V0Sha256) : V0ContentPart()
	
	@Serializable
	@SerialName("io.github.autotweaker.api.types.llm.ContentPart.VideoUrl")
	data class VideoUrl(val url: V0Url) : V0ContentPart()
}
