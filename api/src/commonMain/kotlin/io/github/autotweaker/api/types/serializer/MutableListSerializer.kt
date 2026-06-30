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

package io.github.autotweaker.api.types.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class MutableListSerializer<T>(
	elementSerializer: KSerializer<T>,
) : KSerializer<MutableList<T>> {
	private val delegate = ListSerializer(elementSerializer)
	
	override val descriptor: SerialDescriptor get() = delegate.descriptor
	
	override fun deserialize(decoder: Decoder): MutableList<T> =
		delegate.deserialize(decoder).toMutableList()
	
	override fun serialize(encoder: Encoder, value: MutableList<T>) =
		delegate.serialize(encoder, value)
}
