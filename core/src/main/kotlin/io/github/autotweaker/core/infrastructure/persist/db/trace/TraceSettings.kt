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

package io.github.autotweaker.core.infrastructure.persist.db.trace

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.LongSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef


object TraceSettings {
	@AutoService(SettingDef::class)
	class MaxEntriesPerNamespace : IntSetting(
		1_000_000, zh(
			"Traces数据库每个命名空间最多保留的条目数，设为0忽略命名空间条目计数"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxTotalEntries : IntSetting(
		5_000_000, zh(
			"Traces数据库总条目上限，设为0忽略总数"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxAgeDays : IntSetting(
		120, zh(
			"Traces数据库条目保留天数，设为0忽略天数"
		)
	)
	
	@AutoService(SettingDef::class)
	class MaxDbSizeMB : LongSetting(
		5120, zh(
			"Traces数据库文件大小上限，单位MB，设为0忽略大小"
		)
	)
	
	@AutoService(SettingDef::class)
	class CleanupBatchSize : IntSetting(
		500, zh(
			"Traces数据库文件大小超限时每轮删除的条目数"
		)
	)
	
	@AutoService(SettingDef::class)
	class CleanupIntervalMinutes : IntSetting(
		60, zh(
			"Traces数据库自动清理间隔，单位分钟，设为0禁用自动清理"
		)
	)
}
