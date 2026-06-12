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

package io.github.autotweaker.core.infrastructure.persistence.trace

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue

object TraceSettings {
	@AutoService(SettingDef::class)
	class MaxEntriesPerNamespace : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(1_000_000)
		override val description = "Traces数据库每个命名空间最多保留的条目数，设为0忽略命名空间条目计数"
	}
	
	@AutoService(SettingDef::class)
	class MaxTotalEntries : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(5_000_000)
		override val description = "Traces数据库总条目上限，设为0忽略总数"
	}
	
	@AutoService(SettingDef::class)
	class MaxAgeDays : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(120)
		override val description = "Traces数据库条目保留天数，设为0忽略天数"
	}
	
	@AutoService(SettingDef::class)
	class MaxDbSizeMB : SettingDef<SettingValue.ValLong> {
		override val default = SettingValue.ValLong(5120)
		override val description = "Traces数据库文件大小上限，单位MB，设为0忽略大小"
	}
	
	@AutoService(SettingDef::class)
	class CleanupBatchSize : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(500)
		override val description = "Traces数据库文件大小超限时每轮删除的条目数"
	}
	
	@AutoService(SettingDef::class)
	class CleanupIntervalMinutes : SettingDef<SettingValue.ValInt> {
		override val default = SettingValue.ValInt(60)
		override val description = "Traces数据库自动清理间隔，单位分钟，设为0禁用自动清理"
	}
}
