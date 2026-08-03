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

package io.github.autotweaker.core.infrastructure.data

import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class TemporaryStorageImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	@BeforeTest
	fun setUp() {
		// 清空容器临时目录，保证测试隔离
		TemporaryStorageImpl.list(container = true).values.forEach { it.deleteIfExists() }
	}
	
	@AfterTest
	fun tearDown() {
		TemporaryStorageImpl.list(container = true).values.forEach { it.deleteIfExists() }
	}
	
	@Test
	fun `save writes content and returns path under tmp dir`() {
		val (id, path) = TemporaryStorageImpl.save("hello content", container = true)
		
		assertEquals("hello content", path.readText())
		assertEquals(id.toString(), path.fileName.toString())
		assertTrue(path.startsWith(ContainerConfig().tmpHostPath))
	}
	
	@Test
	fun `save rejects empty content`() {
		assertFailsWith<IllegalArgumentException> {
			TemporaryStorageImpl.save("", container = true)
		}
	}
	
	@Test
	fun `read returns saved content`() {
		val (id, _) = TemporaryStorageImpl.save("payload", container = true)
		
		assertEquals("payload", TemporaryStorageImpl.read(id, container = true))
	}
	
	@Test
	fun `read missing id returns null`() {
		assertNull(TemporaryStorageImpl.read(java.util.UUID.randomUUID(), container = true))
	}
	
	@Test
	fun `list returns saved ids`() {
		val (id1, _) = TemporaryStorageImpl.save("one", container = true)
		val (id2, _) = TemporaryStorageImpl.save("two", container = true)
		
		val listed = TemporaryStorageImpl.list(container = true)
		
		assertEquals(setOf(id1, id2), listed.keys)
		assertEquals("one", listed[id1]?.readText())
	}
	
	@Test
	fun `list ignores non-uuid files`() {
		Files.createDirectories(ContainerConfig().tmpHostPath)
		val junk = ContainerConfig().tmpHostPath.resolve("not-a-uuid")
		junk.writeText("junk")
		try {
			TemporaryStorageImpl.save("real", container = true)
			
			val listed = TemporaryStorageImpl.list(container = true)
			
			assertEquals(1, listed.size)
		} finally {
			// 非 uuid 文件不会被 list 返回，tearDown 清不到，需自行清理
			junk.deleteIfExists()
		}
	}
	
	@Test
	fun `list on missing directory returns empty`() {
		// 空目录时 base 目录被删除后 list 返回空
		ContainerConfig().tmpHostPath.deleteIfExists()
		
		assertTrue(TemporaryStorageImpl.list(container = true).isEmpty())
	}
}
