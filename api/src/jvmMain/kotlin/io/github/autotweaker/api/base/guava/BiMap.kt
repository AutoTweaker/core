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

package io.github.autotweaker.api.base.guava

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.collect.ImmutableBiMap

fun <K, V> biMapOf(): BiMap<K, V> = HashBiMap.create()

fun <K, V> biMapOf(vararg pair: Pair<K, V>): BiMap<K, V> = HashBiMap.create(pair.toMap())

fun <K, V> Map<K, V>.toBiMap(): BiMap<K, V> = HashBiMap.create(this)

fun <K : Any, V : Any> BiMap<K, V>.toImmutable() = ImmutableBiMap.copyOf(this)

val <K, V> BiMap<K, V>.inverse get() = inverse()
