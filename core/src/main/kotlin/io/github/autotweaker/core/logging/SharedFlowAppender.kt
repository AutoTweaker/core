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

package io.github.autotweaker.core.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.UnsynchronizedAppenderBase
import io.github.autotweaker.api.discard
import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import io.github.autotweaker.api.types.log.LogLevel
import io.github.autotweaker.core.application.impl.LogBus
import kotlin.time.Instant

class SharedFlowAppender : UnsynchronizedAppenderBase<ILoggingEvent>() {
	override fun append(event: ILoggingEvent) =
		LogBus.emit(event.toLogEvent()).discard()
	
	private fun ILoggingEvent.toLogEvent(): LogEvent<ExceptionInfo.Live> {
		val ex = (throwableProxy as? ThrowableProxy)?.throwable
		return LogEvent(
			timestamp = Instant.fromEpochMilliseconds(timeStamp),
			level = level.toLogLevel(),
			thread = threadName,
			logger = loggerName,
			message = formattedMessage,
			exception = ex?.let { ExceptionInfo.Live(it) }
		)
	}
	
	private fun Level.toLogLevel(): LogLevel = when (this) {
		Level.TRACE -> LogLevel.TRACE
		Level.DEBUG -> LogLevel.DEBUG
		Level.INFO -> LogLevel.INFO
		Level.WARN -> LogLevel.WARN
		Level.ERROR -> LogLevel.ERROR
		else -> LogLevel.INFO
	}
}
