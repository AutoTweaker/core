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

package io.github.autotweaker.core.tool

import io.github.autotweaker.core.tool.impl.bash.BashService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private interface AnotherService

class SimpleContainerTest {
    
    @Test
    fun `register and get roundtrip`() {
        val container = SimpleContainer()
        val service = mockk<BashService>()
        container.register(BashService::class, service)
        
        val resolved = container.get(BashService::class)
        assertSame(service, resolved)
    }
    
    @Test
    fun `get throws NoSuchElementException when not registered`() {
        val container = SimpleContainer()
        val ex = assertFailsWith<NoSuchElementException> {
            container.get(BashService::class)
        }
        assertTrue(ex.message!!.contains("BashService"))
    }
    
    @Test
    fun `register overwrites existing service`() {
        val container = SimpleContainer()
        val oldService = mockk<BashService>()
        val newService = mockk<BashService>()
        
        container.register(BashService::class, oldService)
        container.register(BashService::class, newService)
        
        val resolved = container.get(BashService::class)
        assertSame(newService, resolved)
    }
    
    @Test
    fun `multiple different services coexist`() {
        val container = SimpleContainer()
        val bashService = mockk<BashService>()
        val anotherService = mockk<AnotherService>()
        
        container.register(BashService::class, bashService)
        container.register(AnotherService::class, anotherService)
        
        assertSame(bashService, container.get(BashService::class))
        assertSame(anotherService, container.get(AnotherService::class))
    }
    
    @Test
    fun `get with inline extension roundtrip`() {
        val container = SimpleContainer()
        val service = mockk<BashService>()
        container.register(BashService::class, service)
        
        val resolved: BashService = container.get()
        assertSame(service, resolved)
    }
}
