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

package io.github.autotweaker.core.infrastructure.persist

import io.github.autotweaker.api.*
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import io.github.autotweaker.api.types.log.LogLevel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Instant

object LogStore : Loggable, Traceable {
	private val json = Json { ignoreUnknownKeys = true }
	private val logDir: Path = CONFIG_PATH.resolve("logs")
	
	fun readLogs(start: Instant, end: Instant): List<LogEvent<ExceptionInfo.Stored>> {
		val files = trace.catching {
			Files.list(logDir).use { stream ->
				stream.filter { it.fileName.toString().endsWith(".jsonl") }.toList()
			}
		}.getOrNull()?.sortedByDescending { it.fileName.toString() }.orEmpty()
		
		val startDate = start.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
		val result = mutableListOf<LogEvent<ExceptionInfo.Stored>>()
		
		for (file in files) {
			val fileName = file.fileName.toString()
			if (fileName.substringBeforeLast('.') < "$APP_NAME_LOWERCASE.$startDate") break
			trace.catching {
				Files.readAllLines(file)
			}.getOrNull()?.asReversed()?.forEach { line ->
				val event = parseLine(line) ?: return@forEach
				if (event.timestamp > end) return@forEach
				if (event.timestamp < start) return@forEach
				result.add(event)
			}
		}
		return result
	}
	
	private fun parseLine(line: String): LogEvent<ExceptionInfo.Stored>? =
		trace.catching {
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
		}.onFailure { log.debug("Failed JSONL line parsing  length={}  reason={}", line.length, it.message) }
			.getOrNull()
	
	private fun parseInstant(value: String): Instant? =
		trace.catching { Instant.parse(value) }.getOrNull()
	
	private fun String.toLogLevel(): LogLevel? = when (uppercase()) {
		"TRACE" -> LogLevel.TRACE
		"DEBUG" -> LogLevel.DEBUG
		"INFO" -> LogLevel.INFO
		"WARN", "WARNING" -> LogLevel.WARN
		"ERROR" -> LogLevel.ERROR
		else -> null
	}
}
