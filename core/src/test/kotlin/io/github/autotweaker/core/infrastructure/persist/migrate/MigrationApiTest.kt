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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

class MigrationApiTest {
	
	@BeforeTest
	fun cleanUp() {
		MigrateTestEnv.clean()
	}
	
	@Test
	fun `json roundtrip and missing namespace`() = runBlocking {
		SchemaMigrationEngine.run()
		MigrateTestEnv.createJsonStoreTable()
		val element: JsonElement = buildJsonObject { put("k", JsonPrimitive("v")) }
		MigrationApi.writeJson("test.ns", element)
		val stored: JsonElement = MigrationApi.readJson("test.ns") ?: fail("Stored json is missing")
		assertEquals(element, stored)
		assertNull(MigrationApi.readJson("missing.ns"))
	}
	
	
	@Test
	fun `syncSchema adds missing column idempotently`() = runBlocking {
		SchemaMigrationEngine.run()
		MigrateTestEnv.createLegacyAppConfig()
		MigrationApi.syncSchema(MigrationApi.APP_CONFIG, MigratedColumnTable)
		MigrationApi.syncSchema(MigrationApi.APP_CONFIG, MigratedColumnTable)
	}
}
