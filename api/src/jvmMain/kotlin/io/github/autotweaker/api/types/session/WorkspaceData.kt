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

package io.github.autotweaker.api.types.session

import io.github.autotweaker.api.UUID
import io.github.autotweaker.api.types.serializer.PathSerializer
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.*
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class WorkspaceData(
	@Serializable(with = UuidSerializer::class)
	val id: UUID = UUID(),
	@Serializable(with = PathSerializer::class)
	val path: Path,
	val displayName: String,
	val creationTime: Instant = Clock.System.now(),
	val lastAccessTime: Instant = Clock.System.now(),
	val sessionIds: Set<@Serializable(with = UuidSerializer::class) UUID> = emptySet()
)
