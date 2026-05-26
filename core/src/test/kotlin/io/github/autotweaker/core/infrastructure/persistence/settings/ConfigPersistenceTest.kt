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

package io.github.autotweaker.core.infrastructure.persistence.settings

import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.core.infrastructure.persistence.config.ConfigTable
import io.github.autotweaker.core.infrastructure.persistence.config.fillColumn
import io.github.autotweaker.core.infrastructure.persistence.config.getValueFromRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.test.*

class ConfigPersistenceTest {
	
	private val json = Json { ignoreUnknownKeys = true }
	
	@Test
	fun `fillColumn writes serialized JSON`() {
		val builder = mockk<UpdateBuilder<*>>(relaxed = true)
		val jsonSlot = slot<String>()
		
		every { builder[ConfigTable.valJson] = capture(jsonSlot) } answers {}
		
		val value = SettingValue.ValInt(42)
		fillColumn(builder, value)
		
		val captured = jsonSlot.captured
		val decoded = json.decodeFromString(SettingValue.serializer(), captured)
		assertNotNull(decoded)
	}
	
	@Test
	fun `fillColumn with ValString`() {
		val builder = mockk<UpdateBuilder<*>>(relaxed = true)
		val jsonSlot = slot<String>()
		every { builder[ConfigTable.valJson] = capture(jsonSlot) } answers {}
		
		fillColumn(builder, SettingValue.ValString("hello"))
		
		val decoded = json.decodeFromString(SettingValue.serializer(), jsonSlot.captured)
		assertIs<SettingValue.ValString>(decoded)
		assertEquals("hello", decoded.value)
	}
	
	@Test
	fun `fillColumn with ValBoolean`() {
		val builder = mockk<UpdateBuilder<*>>(relaxed = true)
		val jsonSlot = slot<String>()
		every { builder[ConfigTable.valJson] = capture(jsonSlot) } answers {}
		
		fillColumn(builder, SettingValue.ValBoolean(true))
		
		val decoded = json.decodeFromString(SettingValue.serializer(), jsonSlot.captured)
		assertIs<SettingValue.ValBoolean>(decoded)
		assertEquals(true, decoded.value)
	}
	
	@Test
	fun `getValueFromRow parses valid JSON`() {
		val row = mockk<ResultRow>()
		every { row[ConfigTable.valJson] } returns """{"type":"io.github.autotweaker.api.types.config.SettingValue.ValInt","value":42}"""
		
		val result = getValueFromRow(row)
		assertNotNull(result)
		assertIs<SettingValue.ValInt>(result)
		assertEquals(42, result.value)
	}
	
	@Test
	fun `getValueFromRow returns null for invalid JSON`() {
		val row = mockk<ResultRow>()
		every { row[ConfigTable.valJson] } returns "not valid json"
		
		val result = getValueFromRow(row)
		assertNull(result)
	}
	
	@Test
	fun `fillColumn roundtrip with getValueFromRow`() {
		val builder = mockk<UpdateBuilder<*>>(relaxed = true)
		val jsonSlot = slot<String>()
		every { builder[ConfigTable.valJson] = capture(jsonSlot) } answers {}
		
		val original = SettingValue.ValDouble(3.14159)
		fillColumn(builder, original)
		
		val row = mockk<ResultRow>()
		every { row[ConfigTable.valJson] } returns jsonSlot.captured
		
		val restored = getValueFromRow(row)
		assertNotNull(restored)
		assertIs<SettingValue.ValDouble>(restored)
		assertEquals(3.14159, restored.value)
	}
}
