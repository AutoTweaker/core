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

import io.github.autotweaker.api.types.exception.*
import io.github.autotweaker.api.types.exception.duplicate.*
import io.github.autotweaker.api.types.exception.notfound.*
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
	
	private fun workspaceData(id: UUID = UUID.randomUUID(), sessionIds: Set<UUID> = emptySet()) = WorkspaceData(
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
		val ex = assertFailsWith<InvalidWorkspacePathException> {
			WorkspaceAPI.create(relative)
		}
		
		assertTrue(ex.message.contains(expectedPath.toString()))
	}
	
	@Test
	fun `create duplicate display name fails`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.create(any()) } throws DuplicateWorkspaceNameException(data.meta.displayName)
		
		assertFailsWith<DuplicateWorkspaceNameException> {
			WorkspaceAPI.create(data.meta)
		}
	}
	
	@Test
	fun `create non-directory path fails`() = runTest {
		val meta = WorkspaceMeta(displayName = "missing", path = dir.resolve("nope"))
		
		assertFailsWith<InvalidWorkspacePathException> {
			WorkspaceAPI.create(meta)
		}
	}
	
	// endregion
	
	// region rename
	
	@Test
	fun `rename updates workspace meta`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.getData(data.meta.id) } returns data
		coEvery { WorkspaceManager.updateMeta(any()) } coAnswers {
			val meta = firstArg<suspend () -> WorkspaceMeta>()()
			assertEquals("new name", meta.displayName)
		}
		
		WorkspaceAPI.rename(data.meta.id, "new name")
		
		coVerify { WorkspaceManager.updateMeta(any()) }
	}
	
	@Test
	fun `rename duplicate name fails`() = runTest {
		val data = workspaceData()
		coEvery { WorkspaceManager.getData(data.meta.id) } returns data
		coEvery { WorkspaceManager.updateMeta(any()) } throws DuplicateWorkspaceNameException(data.meta.displayName)
		
		assertFailsWith<DuplicateWorkspaceNameException> {
			WorkspaceAPI.rename(data.meta.id, data.meta.displayName)
		}
	}
	
	@Test
	fun `rename missing workspace fails`() = runTest {
		coEvery { WorkspaceManager.getData(any()) } returns null
		coEvery { WorkspaceManager.updateMeta(any()) } coAnswers {
			firstArg<suspend () -> WorkspaceMeta>()()
			Unit
		}
		
		assertFailsWith<WorkspaceNotFoundException> {
			WorkspaceAPI.rename(UUID.randomUUID(), "x")
		}
	}
	
	// endregion
	
	// region delete
	
	@Test
	fun `delete returns false for missing workspace`() = runTest {
		coEvery { WorkspaceManager.delete(any()) } returns false
		
		assertFalse(WorkspaceAPI.delete(UUID.randomUUID()))
	}
	
	@Test
	fun `delete delegates to workspace manager`() = runTest {
		val data = workspaceData(sessionIds = setOf(UUID.randomUUID()))
		coEvery { WorkspaceManager.delete(data.meta.id) } returns true
		
		val deleted = WorkspaceAPI.delete(data.meta.id)
		
		assertTrue(deleted)
		coVerify { WorkspaceManager.delete(data.meta.id) }
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
