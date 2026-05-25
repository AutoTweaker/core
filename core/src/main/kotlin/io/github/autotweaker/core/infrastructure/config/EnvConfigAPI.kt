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

import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.core.domain.port.EnvRepository
import io.github.autotweaker.core.domain.tool.impl.bash.Bash
import io.github.autotweaker.core.infrastructure.container.ContainerManager

object EnvConfigAPI : EnvRepository {
	private val bash = Bash()
	private val con = ContainerManager
	
	override fun list(type: CoreConfig.JsonConfig.Env.Type): List<String> =
		if (type == CoreConfig.JsonConfig.Env.Type.BASH_ENV) {
			bash.listEnv()
		} else {
			con.listEnv()
		}
	
	override fun set(env: List<CoreConfig.JsonConfig.Env>) {
		val bashEnv = env.filter { it.type == CoreConfig.JsonConfig.Env.Type.BASH_ENV }
		val conEnv = env.filter { it.type == CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV }
		bashEnv.forEach { bash.setEnv(it.id, it.value) }
		con.setEnv(conEnv.associateBy({ it.id }, { it.value }))
	}
	
	override fun get(type: CoreConfig.JsonConfig.Env.Type, id: String): String? =
		if (type == CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV) {
			con.getEnv(id)[id]
		} else {
			bash.getEnv(id)
		}
	
	override fun remove(type: CoreConfig.JsonConfig.Env.Type, id: String) =
		if (type == CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV) {
			con.removeEnv(id)
		} else {
			bash.removeEnv(id)
		}
}