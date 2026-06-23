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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.setting
import java.nio.file.Files
import java.nio.file.Path

object TraceCleanup : Loggable, Settable {
	private const val BYTES_PER_MB = 1024 * 1024
	private val dbFilePath = Path.of(
		System.getProperty("user.home"), ".config", "autotweaker", "database", "Traces.mv.db"
	)
	
	fun cleanup() {
		val maxAgeDays = setting.get(TraceSettings.MaxAgeDays()).value
		val maxEntriesPerNs = setting.get(TraceSettings.MaxEntriesPerNamespace()).value
		val maxTotalEntries = setting.get(TraceSettings.MaxTotalEntries()).value
		val maxDbSizeMB = setting.get(TraceSettings.MaxDbSizeMB()).value
		val batchSize = setting.get(TraceSettings.CleanupBatchSize()).value
		
		var cleanupCount = 0
		
		cleanupCount += if (maxAgeDays > 0) TraceStore.deleteByAge(maxAgeDays) else 0
		cleanupCount += if (maxEntriesPerNs > 0) TraceStore.trimPerNamespace(maxEntriesPerNs) else 0
		cleanupCount += if (maxTotalEntries > 0) TraceStore.trimGlobal(maxTotalEntries) else 0
		cleanupCount += if (maxDbSizeMB > 0 && batchSize > 0) {
			val maxSizeBytes = maxDbSizeMB * BYTES_PER_MB
			if (Files.size(dbFilePath) > maxSizeBytes) {
				TraceStore.deleteOldestBatch(batchSize)
			} else 0
		} else 0
		
		if (cleanupCount > 0) log.info("Completed trace cleanup  count={}", cleanupCount)
	}
}
