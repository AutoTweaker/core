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

package io.github.autotweaker.api

import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.types.KebabCase
import io.github.autotweaker.api.types.UpperSnakeCase

object TestServices {
	private val traceRecorder = object : TraceRecorder {
		override fun add(namespace: KebabCase, content: Any) {}
		override fun add(namespace: KebabCase, content: Map<UpperSnakeCase, Any>) {}
	}
	
	fun init() {
		try {
			initServices(
				ServiceRegistry(
					trace = { traceRecorder },
					store = { error("Test store not configured") },
					lazyObjects = { error("Test objects not configured") },
					lazySetting = { error("Test setting not configured") },
					lazyI18n = { error("Test i18n not configured") },
				)
			)
		} catch (_: IllegalStateException) {
		}
	}
}
