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

package io.github.autotweaker.core.infrastructure.persist.db.usage

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object UsageTable : Table("usage_record") {
	val id = javaUUID("id")
	val modelId = javaUUID("model_id")
	val timestamp = timestamp("timestamp")
	val promptTokens = integer("prompt_tokens")
	val completionTokens = integer("completion_tokens")
	val reasoningTokens = integer("reasoning_tokens").nullable()
	val cacheHitTokens = integer("cache_hit_tokens").nullable()
	
	override val primaryKey = PrimaryKey(id)
	
	init {
		index(false, modelId)
		index(false, timestamp)
	}
}
