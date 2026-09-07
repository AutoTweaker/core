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

package io.github.autotweaker.core.infrastructure.persist.migrate

import io.github.autotweaker.api.hook.StartupHook
import io.github.autotweaker.core.infrastructure.loadClass
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupHookDiscoveryTest {
	
	@Test
	fun `builtin migration hook is discovered by default service loader`() {
		val hooks = ServiceLoader.load(StartupHook::class.java).map { it::class.java.name }.toList()
		assertTrue(hooks.contains(DataMigrationStartupHook::class.java.name))
	}
	
	@Test
	fun `combined load returns exactly one builtin hook`() {
		val hooks = loadClass<StartupHook>().toList()
		assertEquals(1, hooks.size)
		assertEquals(DataMigrationStartupHook::class.java, hooks.single()::class.java)
	}
}
