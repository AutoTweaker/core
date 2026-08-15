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

package io.github.autotweaker.core.domain.port

import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageCursor
import io.github.autotweaker.api.types.llm.UsageEntry
import java.util.*
import kotlin.time.Instant

interface UsageRepository {
	suspend fun save(usages: List<UsageEntry>)
	suspend fun load(ids: Set<UUID>): List<UsageEntry>
	suspend fun load(limit: Int, before: UsageCursor?): List<UsageEntry>
	suspend fun summarize(ids: Set<UUID>): Usage?
	suspend fun summarize(modelId: UUID?, from: Instant?, to: Instant?): Usage?
}
