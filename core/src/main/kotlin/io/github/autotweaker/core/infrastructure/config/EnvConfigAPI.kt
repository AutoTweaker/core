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
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.config.CoreConfig.JsonConfig.Env.Type
import io.github.autotweaker.core.domain.port.EnvRepository
import io.github.autotweaker.core.domain.tool.impl.bash.Bash
import io.github.autotweaker.core.infrastructure.container.ContainerManager
import io.github.autotweaker.core.infrastructure.persistence.EnvStore

object EnvConfigAPI : EnvRepository, Loggable {
	override suspend fun list(type: Type): List<String> = store(type).listEnv()
	override suspend fun set(env: CoreConfig.JsonConfig.Env) = store(env.type).setEnv(env.id, env.value)
	override suspend fun get(type: Type, id: String): String? = store(type).getEnv(id)
	override suspend fun remove(type: Type, id: String) = store(type).removeEnv(id)
	
	private fun store(type: Type): EnvStore = when (type) {
		Type.BASH_ENV -> Bash.Companion
		Type.CONTAINER_ENV -> ContainerManager
	}
}
