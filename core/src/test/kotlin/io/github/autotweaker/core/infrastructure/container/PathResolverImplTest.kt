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

package io.github.autotweaker.core.infrastructure.container

import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import io.github.autotweaker.core.TestServices
import java.nio.file.Path
import kotlin.test.*

class PathResolverImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val config = ContainerConfig(
		workDir = Path.of("/workspace"),
		workspaceHostPath = Path.of("/host/ws"),
		tmpHostPath = Path.of("/host/tmp"),
		containerTmpPath = Path.of("/container/tmp"),
	)
	private val resolver = PathResolverImpl(config)
	
	// region inContainer
	
	@Test
	fun `inContainer true for workspace and descendants`() {
		assertTrue(resolver.inContainer(Path.of("/host/ws")))
		assertTrue(resolver.inContainer(Path.of("/host/ws/file.txt")))
		assertTrue(resolver.inContainer(Path.of("/host/ws/a/b/c")))
	}
	
	@Test
	fun `inContainer false for unrelated paths and prefix siblings`() {
		assertFalse(resolver.inContainer(Path.of("/home/user")))
		assertFalse(resolver.inContainer(Path.of("/host")))
		assertFalse(resolver.inContainer(Path.of("/host/ws2")))
		assertFalse(resolver.inContainer(Path.of("/host/ws-other")))
	}
	
	// endregion
	
	// region toContainerPath
	
	@Test
	fun `toContainerPath maps workspace paths`() {
		assertEquals(
			Path.of("/workspace/a/b.txt"),
			resolver.toContainerPath(Path.of("/host/ws/a/b.txt"))
		)
	}
	
	@Test
	fun `toContainerPath maps tmp paths`() {
		assertEquals(
			Path.of("/container/tmp/x"),
			resolver.toContainerPath(Path.of("/host/tmp/x"))
		)
	}
	
	@Test
	fun `toContainerPath normalizes input`() {
		assertEquals(
			Path.of("/workspace/b"),
			resolver.toContainerPath(Path.of("/host/ws/a/../b"))
		)
	}
	
	@Test
	fun `toContainerPath throws for paths outside both roots`() {
		assertFailsWith<PathOutsideWorkspaceException> {
			resolver.toContainerPath(Path.of("/etc/passwd"))
		}
		assertFailsWith<PathOutsideWorkspaceException> {
			resolver.toContainerPath(Path.of("/host/ws2/file"))
		}
	}
	
	// endregion
	
	// region toHostPath
	
	@Test
	fun `toHostPath maps workspace paths`() {
		assertEquals(
			Path.of("/host/ws/a/b.txt"),
			resolver.toHostPath(Path.of("/workspace/a/b.txt"))
		)
	}
	
	@Test
	fun `toHostPath maps tmp paths`() {
		assertEquals(
			Path.of("/host/tmp/x"),
			resolver.toHostPath(Path.of("/container/tmp/x"))
		)
	}
	
	@Test
	fun `toHostPath throws for paths outside both roots`() {
		assertFailsWith<PathOutsideWorkspaceException> {
			resolver.toHostPath(Path.of("/other"))
		}
	}
	
	// endregion
	
	// region toAbsolutePath
	
	@Test
	fun `toAbsolutePath resolves relative to container workspace when workspace is in container`() {
		assertEquals(
			Path.of("/workspace/a/b"),
			resolver.toAbsolutePath(Path.of("/host/ws"), Path.of("a/b"))
		)
	}
	
	@Test
	fun `toAbsolutePath resolves relative to host workspace otherwise`() {
		assertEquals(
			Path.of("/home/user/a/b"),
			resolver.toAbsolutePath(Path.of("/home/user"), Path.of("a/b"))
		)
	}
	
	@Test
	fun `toAbsolutePath normalizes result`() {
		assertEquals(
			Path.of("/workspace/b"),
			resolver.toAbsolutePath(Path.of("/host/ws"), Path.of("a/../b"))
		)
	}
	
	// endregion
}
