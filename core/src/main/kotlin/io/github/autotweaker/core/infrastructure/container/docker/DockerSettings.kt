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

package io.github.autotweaker.core.infrastructure.container.docker

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object DockerSettings {
	@AutoService(SettingDef::class)
	class PermissionFixDelaySeconds : IntSetting(
		300, zh(
			"指定秒数内未运行容器内命令自动修复工作区目录权限，设为0仅在应用退出前修复"
		)
	)
	
	@AutoService(SettingDef::class)
	class KillAfterSeconds : IntSetting(
		10, zh(
			"容器内命令超时后发送KILL信号的等待时长（秒），TERM信号发出后命令仍未退出则在此时间后强制KILL"
		)
	)
}
