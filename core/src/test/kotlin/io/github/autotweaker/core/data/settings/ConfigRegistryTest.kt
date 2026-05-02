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

package io.github.autotweaker.core.data.settings

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigRegistryTest {
    
    // This test class doesn't mock CoreConfigRegistry, so the real init runs.
    // SerializeConfig caches results across tests; the cache was populated by
    // a previous Gradle task, so loadDefaultConfig() succeeds (try branch).
    // getItem/getAllItems are tested with real cached data.
    
    @Test
    fun `getItem returns null for unknown key`() {
        val result = CoreConfigRegistry.getItem("nonexistent.zz99")
        assertNull(result)
    }
    
    @Test
    fun `getAllItems returns items loaded from cache`() {
        val all = CoreConfigRegistry.getAllItems()
        assertTrue(all.isNotEmpty())
    }
    
    @Test
    fun `getItem finds item by key`() {
        val all = CoreConfigRegistry.getAllItems()
        if (all.isNotEmpty()) {
            val first = all.first()
            val result = CoreConfigRegistry.getItem(first.key.value)
            assertNotNull(result)
        }
    }
}
