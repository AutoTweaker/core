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

package io.github.autotweaker.api.types.llm

import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.Url
import kotlinx.serialization.Serializable

/**
 * 表示一个用户输入类型，请使用 [io.github.autotweaker.api.ObjectStorable] 获取 [io.github.autotweaker.api.store.ObjectStorage]，并调用 [io.github.autotweaker.api.store.ObjectStorage.put] 来存储用户提供的媒体文件。
 *
 * 无效的 [Sha256] 无法保证行为，取决于 [io.github.autotweaker.api.llm.LlmClient]，请确保 [Sha256] 有效。
 *
 * [io.github.autotweaker.api.store.ObjectStorage] 不具备自动清理机制，只要 [Sha256] 来自 [io.github.autotweaker.api.store.ObjectStorage.put] 就不会出现问题。
 */
@Serializable
sealed class ContentPart {
	@Serializable
	data class Text(
		val content: String,
	) : ContentPart()
	
	@Serializable
	data class Image(
		/**
		 * MIME 类型，如 `image/jpeg`。
		 */
		val mimeType: String,
		/**
		 * 必须为一个存储在 [io.github.autotweaker.api.store.ObjectStorage] 中的文件。
		 */
		val data: Sha256
	) : ContentPart()
	
	@Serializable
	data class ImageUrl(
		val url: Url
	) : ContentPart()
	
	@Serializable
	data class Audio(
		/**
		 * MIME 类型，如 `audio/mpeg`。
		 */
		val mimeType: String,
		/**
		 * 必须为一个存储在 [io.github.autotweaker.api.store.ObjectStorage] 中的文件。
		 */
		val data: Sha256
	) : ContentPart()
	
	@Serializable
	data class AudioUrl(
		val url: Url
	) : ContentPart()
	
	@Serializable
	data class Video(
		/**
		 * MIME 类型，如 `video/mp4`。
		 */
		val mimeType: String,
		/**
		 * 必须为一个存储在 [io.github.autotweaker.api.store.ObjectStorage] 中的文件。
		 */
		val data: Sha256
	) : ContentPart()
	
	@Serializable
	data class VideoUrl(
		val url: Url
	) : ContentPart()
}

fun String.toContentPart() = listOf(ContentPart.Text(this))
