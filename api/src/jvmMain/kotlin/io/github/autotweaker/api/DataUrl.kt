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

@file:Suppress("FunctionName")

package io.github.autotweaker.api

import io.github.autotweaker.api.types.Sha256
import kotlin.io.encoding.Base64

/**
 * 生成一个符合 RFC 2397 的 data URL
 */
suspend fun ObjectStorable.DataUrl(mediatype: String, data: Sha256) =
	objects.get(data)?.let {
		DataUrl(mediatype, it)
	}

/**
 * 生成一个符合 RFC 2397 的 data URL
 */
fun DataUrl(mediatype: String, data: ByteArray) =
	"data:${mediatype};base64,${Base64.encode(data)}"
