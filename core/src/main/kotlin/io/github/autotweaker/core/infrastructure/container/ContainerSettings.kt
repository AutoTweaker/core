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

package io.github.autotweaker.core.infrastructure.container

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object ContainerSettings {
	@AutoService(SettingDef::class)
	class DockerImage : StringSetting(
		"buildpack-deps:stable", zh(
			"容器内工作区所使用的docker镜像id"
		)
	)
	
	@AutoService(SettingDef::class)
	class AccessDeniedMessage : StringSetting(
		"无法运行容器内命令：程序无法连接到容器服务，请确保容器服务已安装，并且程序所在用户拥有足够的权限。\n对于Docker，请确保程序所在用户拥有'docker'用户组",
		zh(
			"容器访问被拒绝时容器内命令返回的消息"
		)
	)
}
