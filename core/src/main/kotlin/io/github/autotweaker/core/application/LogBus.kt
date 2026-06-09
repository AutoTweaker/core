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

package io.github.autotweaker.core.application

import io.github.autotweaker.api.types.log.ExceptionInfo
import io.github.autotweaker.api.types.log.LogEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object LogBus {
	private val _flow = MutableSharedFlow<LogEvent<ExceptionInfo.Live>>(replay = 1000)
	val flow: SharedFlow<LogEvent<ExceptionInfo.Live>> = _flow
	
	fun emit(event: LogEvent<ExceptionInfo.Live>) {
		_flow.tryEmit(event)
	}
}
