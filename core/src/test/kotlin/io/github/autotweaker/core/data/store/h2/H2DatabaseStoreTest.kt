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

package io.github.autotweaker.core.data.store.h2

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class H2DatabaseStoreTest {
	
	@Test
	fun `connect creates database directory and file`() {
		val tmpHome = Files.createTempDirectory("autotweaker_test")
		val originalHome = System.getProperty("user.home")
		try {
			System.setProperty("user.home", tmpHome.toString())
			val store = H2DatabaseStore()
			store.connect("TestDb")
			
			val dbDir = Path.of(tmpHome.toString(), ".config", "autotweaker", "database")
			assertTrue(Files.exists(dbDir))
		} finally {
			if (originalHome != null) {
				System.setProperty("user.home", originalHome)
			}
			tmpHome.toFile().deleteRecursively()
		}
	}
}
