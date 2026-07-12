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

package io.github.autotweaker.core.domain.tool.impl.bash

import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.tool.buildMeta

object BashMeta : Settable {
	fun meta(envIds: suspend () -> String) =
		buildMeta("bash", setting(BashSettings.Description())) {
			function("run") {
				description { setting(BashSettings.RunFuncDescription()) }
				
				string("command") {
					description { setting(BashSettings.CommandPropDescription()) }
				}
				int("timeout_seconds") {
					required = false
					description {
						setting(BashSettings.TimeoutPropDescription())
							.format(setting(BashSettings.DefaultTimeoutSeconds()))
					}
				}
				stringList("env_ids") {
					description { setting(BashSettings.EnvIdsPropDescription()).format(envIds) }
				}
			}
		}
}
