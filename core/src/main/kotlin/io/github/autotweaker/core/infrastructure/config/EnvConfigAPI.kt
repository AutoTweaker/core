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
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.config.CoreConfig.JsonConfig.Env.Type
import io.github.autotweaker.core.domain.port.EnvRepository
import io.github.autotweaker.core.domain.tool.impl.bash.Bash
import io.github.autotweaker.core.infrastructure.container.ContainerManager

object EnvConfigAPI : EnvRepository, Loggable {
	private val con = ContainerManager
	
	override suspend fun list(type: Type): List<String> = when (type) {
		Type.BASH_ENV -> Bash.listEnv()
		Type.CONTAINER_ENV -> con.listEnv()
	}
	
	override suspend fun set(env: List<CoreConfig.JsonConfig.Env>) {
		val bashEnv = env.filter { it.type == Type.BASH_ENV }
		val conEnv = env.filter { it.type == Type.CONTAINER_ENV }
		bashEnv.forEach { Bash.setEnv(it.id, it.value) }
		conEnv.forEach { con.setEnv(it.id, it.value) }
		log.info("Set environment variables  bashCount={}  containerCount={}", bashEnv.size, conEnv.size)
	}
	
	override suspend fun get(type: Type, id: String): String? = when (type) {
		Type.CONTAINER_ENV -> con.getEnv(id)[id]
		Type.BASH_ENV -> Bash.getEnv(id)
	}
	
	override suspend fun remove(type: Type, id: String) {
		when (type) {
			Type.CONTAINER_ENV -> con.removeEnv(id)
			Type.BASH_ENV -> Bash.removeEnv(id)
		}
		log.info("Removed environment variable  type={}  id={}", type, id)
	}
}
