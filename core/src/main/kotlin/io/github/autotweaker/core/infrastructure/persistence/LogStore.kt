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

package io.github.autotweaker.core.infrastructure.persistence

import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import io.github.autotweaker.api.types.log.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Instant

object LogStore {
	private val logger = LoggerFactory.getLogger(LogStore::class.java)
	private val json = Json { ignoreUnknownKeys = true }
	private val logDir = File(System.getProperty("user.home"), ".config/autotweaker/logs")
	
	fun readLogs(): Flow<LogEvent<ExceptionInfo.Stored>> = flow {
		val files = logDir.listFiles { f -> f.extension == "jsonl" }
			?.sortedBy { it.lastModified() }
			?: return@flow
		
		for (file in files) {
			file.bufferedReader().use { reader ->
				var line = reader.readLine()
				while (line != null) {
					val event = parseLine(line)
					if (event != null) emit(event)
					line = reader.readLine()
				}
			}
		}
	}.flowOn(Dispatchers.IO)
	
	private fun parseLine(line: String): LogEvent<ExceptionInfo.Stored>? {
		return try {
			val obj = json.decodeFromString<JsonObject>(line)
			LogEvent(
				timestamp = obj["@timestamp"]?.jsonPrimitive?.content?.let { parseInstant(it) }
					?: Instant.fromEpochMilliseconds(0),
				level = obj["level"]?.jsonPrimitive?.content?.toLogLevel() ?: LogLevel.INFO,
				thread = obj["thread_name"]?.jsonPrimitive?.content.orEmpty(),
				logger = obj["logger_name"]?.jsonPrimitive?.content.orEmpty(),
				message = obj["message"]?.jsonPrimitive?.content.orEmpty(),
				exception = obj["stack_trace"]?.jsonPrimitive?.content?.let { ExceptionInfo.Stored(it) }
			)
		} catch (e: Exception) {
			logger.debug("Failed to parse JSONL line  length={}", line.length, e)
			null
		}
	}
	
	private fun parseInstant(value: String): Instant? {
		return try {
			Instant.parse(value)
		} catch (_: Exception) {
			null
		}
	}
	
	private fun String.toLogLevel(): LogLevel? = when (uppercase()) {
		"TRACE" -> LogLevel.TRACE
		"DEBUG" -> LogLevel.DEBUG
		"INFO" -> LogLevel.INFO
		"WARN", "WARNING" -> LogLevel.WARN
		"ERROR" -> LogLevel.ERROR
		else -> null
	}
}
