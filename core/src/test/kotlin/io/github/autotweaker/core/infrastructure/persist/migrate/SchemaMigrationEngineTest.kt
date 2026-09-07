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

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SchemaMigrationEngineTest {
	
	@BeforeTest
	fun cleanUp() {
		MigrateTestEnv.clean()
	}
	
	@Test
	fun `fresh install initializes schema version`() = runBlocking {
		SchemaMigrationEngine.run()
		assertTrue(MigrateTestEnv.appConfigDbFileExists())
		assertEquals(CURRENT_SCHEMA_VERSION, MigrateTestEnv.readStoredVersion())
	}
	
	@Test
	fun `up to date run is no-op`() = runBlocking {
		SchemaMigrationEngine.run()
		assertEquals(CURRENT_SCHEMA_VERSION, MigrateTestEnv.readStoredVersion())
		SchemaMigrationEngine.run()
		assertEquals(CURRENT_SCHEMA_VERSION, MigrateTestEnv.readStoredVersion())
	}
	
	@Test
	fun `legacy database without version row fails startup`() = runBlocking {
		MigrateTestEnv.createLegacyAppConfig()
		val error = try {
			SchemaMigrationEngine.run()
			null
		} catch (e: Throwable) {
			e
		}
		assertIs<IllegalStateException>(error)
		assertTrue(error.message!!.contains(SCHEMA_VERSION_KEY))
		assertTrue(error.message!!.contains("CREATE TABLE"))
	}
	
	@Test
	fun `database from newer program fails startup`() = runBlocking {
		SchemaMigrationEngine.run()
		MigrateTestEnv.seedSchemaVersion(CURRENT_SCHEMA_VERSION + 5)
		val error = try {
			SchemaMigrationEngine.run()
			null
		} catch (e: Throwable) {
			e
		}
		assertIs<IllegalStateException>(error)
		assertTrue(error.message!!.contains("newer"))
	}
}
