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

package io.github.autotweaker.core.domain.session

import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class WorkspaceAPITest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private lateinit var dir: Path
	
	private fun workspaceData(id: UUID = UUID.randomUUID(), sessionIds: List<UUID>? = null) = WorkspaceData(
		meta = WorkspaceMeta(displayName = "ws-$id", id = id, path = dir),
		sessionIds = sessionIds,
	)
	
	@BeforeTest
	fun setUp() {
		dir = Files.createTempDirectory("workspace-test")
		mockkObject(WorkspaceManager)
		coEvery { WorkspaceManager.getAll() } returns emptyList()
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(WorkspaceManager)
		dir.toFile().deleteRecursively()
	}
	
	// region create
	
	@Test
	fun `create with absolute path delegates to workspace manager`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.create(any()) } returns data
		
		val result = WorkspaceAPI.create(data.meta)
		
		assertEquals(data, result)
		coVerify { WorkspaceManager.create(data.meta) }
	}
	
	@Test
	fun `create resolves relative path against user home`() = runTest {
		val relative = WorkspaceMeta(displayName = "rel", path = Path.of("relative/dir"))
		val expectedPath = Path.of(System.getProperty("user.home")).resolve("relative/dir")
		
		// home 下不存在该目录，isDirectory 检查会失败——但错误消息应包含解析后的绝对路径
		val ex = assertFailsWith<IllegalStateException> {
			WorkspaceAPI.create(relative)
		}
		
		assertTrue(ex.message!!.contains(expectedPath.toString()))
	}
	
	@Test
	fun `create duplicate display name fails`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.getAll() } returns listOf(data)
		coEvery { WorkspaceManager.create(any()) } returns data
		
		assertFailsWith<IllegalArgumentException> {
			WorkspaceAPI.create(data.meta)
		}
	}
	
	@Test
	fun `create non-directory path fails`() = runTest {
		val meta = WorkspaceMeta(displayName = "missing", path = dir.resolve("nope"))
		
		assertFailsWith<IllegalStateException> {
			WorkspaceAPI.create(meta)
		}
	}
	
	// endregion
	
	// region rename
	
	@Test
	fun `rename updates workspace meta`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.getData(data.meta.id) } returns data
		coEvery { WorkspaceManager.updateMeta(any()) } returns Unit
		
		WorkspaceAPI.rename(data.meta.id, "new name")
		
		coVerify { WorkspaceManager.updateMeta(match { it.displayName == "new name" }) }
	}
	
	@Test
	fun `rename duplicate name fails`() = runTest {
		val existing = workspaceData()
		val target = workspaceData()
		coEvery { WorkspaceManager.getData(target.meta.id) } returns target
		coEvery { WorkspaceManager.getAll() } returns listOf(existing)
		
		assertFailsWith<IllegalArgumentException> {
			WorkspaceAPI.rename(target.meta.id, existing.meta.displayName)
		}
	}
	
	@Test
	fun `rename missing workspace fails`() = runTest {
		coEvery { WorkspaceManager.getData(any()) } returns null
		
		assertFailsWith<IllegalStateException> {
			WorkspaceAPI.rename(UUID.randomUUID(), "x")
		}
	}
	
	// endregion
	
	// region delete
	
	@Test
	fun `delete returns false for missing workspace`() = runTest {
		coEvery { WorkspaceManager.getData(any()) } returns null
		
		assertFalse(WorkspaceAPI.delete(UUID.randomUUID()))
	}
	
	@Test
	fun `delete removes sessions and workspace`() = runTest {
		val sessionId = UUID.randomUUID()
		val data = workspaceData(sessionIds = listOf(sessionId))
		coEvery { WorkspaceManager.getData(data.meta.id) } returns data
		coEvery { WorkspaceManager.delete(data.meta.id) } returns true
		mockkObject(SessionManager)
		coEvery { SessionManager.delete(sessionId) } returns true
		
		val deleted = WorkspaceAPI.delete(data.meta.id)
		
		assertTrue(deleted)
		coVerify { SessionManager.delete(sessionId) }
		coVerify { WorkspaceManager.delete(data.meta.id) }
		unmockkObject(SessionManager)
	}
	
	// endregion
	
	// region list
	
	@Test
	fun `list delegates to workspace manager`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.getAll() } returns listOf(data)
		
		val expected = listOf(data)
		assertEquals(expected, WorkspaceAPI.list())
	}
	
	// endregion
}
