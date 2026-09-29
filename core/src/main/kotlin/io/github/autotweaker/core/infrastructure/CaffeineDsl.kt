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

package io.github.autotweaker.core.infrastructure

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Suppress("UNCHECKED_CAST")
fun <K : Any, V : Any> caffeine(block: Caffeine<K, Any>.() -> Unit = {}): Cache<K, V> =
	(Caffeine.newBuilder() as Caffeine<K, Any>).apply(block).build()

fun <K : Any, V : Any> Caffeine<K, V>.expireAfterWrite(duration: Duration): Caffeine<K, V> =
	expireAfterWrite(duration.toJavaDuration())

fun <K : Any, V : Any> Caffeine<K, V>.expireAfterAccess(duration: Duration): Caffeine<K, V> =
	expireAfterAccess(duration.toJavaDuration())

fun <K : Any, V : Any> Caffeine<K, V>.refreshAfterWrite(duration: Duration): Caffeine<K, V> =
	refreshAfterWrite(duration.toJavaDuration())

operator fun <K : Any, V : Any> Cache<K, V>.get(key: K): V? = getIfPresent(key)

operator fun <K : Any, V : Any> Cache<K, V>.set(key: K, value: V) = put(key, value)

operator fun <K : Any, V : Any> Cache<K, V>.contains(key: K): Boolean = asMap().containsKey(key)

@Suppress("UNCHECKED_CAST")
fun <K : Any, V : Any> Cache<K, V>.getOrPut(key: K, load: (K) -> V?): V? =
	(this as Cache<K, V?>).get(key) { load(it) }

fun <K : Any, V : Any> Cache<K, V>.remove(key: K): V? = asMap().remove(key)
