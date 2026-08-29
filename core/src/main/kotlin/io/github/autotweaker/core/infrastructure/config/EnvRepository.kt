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

package io.github.autotweaker.core.infrastructure.config

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.types.config.EnvType
import io.github.autotweaker.core.domain.tool.impl.bash.Bash
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore

class EnvRepository(private val container: ContainerManager) : Loggable {
	suspend fun list(type: EnvType): List<String> = getStore(type).listEnv()
	suspend fun set(type: EnvType, id: String, value: String) = getStore(type).setEnv(id, value)
	suspend fun get(type: EnvType, id: String): String? = getStore(type).getEnv(id)
	suspend fun remove(type: EnvType, id: String) = getStore(type).removeEnv(id)
	
	private fun getStore(type: EnvType): EnvStore = when (type) {
		EnvType.BASH_ENV -> Bash.Companion
		EnvType.CONTAINER_ENV -> container
	}
}
