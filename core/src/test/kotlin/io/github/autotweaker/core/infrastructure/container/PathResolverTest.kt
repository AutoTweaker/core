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
import java.nio.file.Path
import kotlin.test.*

class PathResolverTest {
	private val config = ContainerConfig(
		workspaceHostPath = Path.of("/home/user/.config/autotweaker/container/workspace"),
		tmpHostPath = Path.of("/tmp/autotweaker/container"),
		workDir = Path.of("/workspace"),
		containerTmpPath = Path.of("/tmp/autotweaker"),
	)
	private val resolver = PathResolverImpl(config)
	
	// region inContainer
	
	@Test
	fun `inContainer returns true for workspace under hostWorkspace`() {
		val workspace = Path.of("/home/user/.config/autotweaker/container/workspace/myproject")
		assertTrue(resolver.inContainer(workspace))
	}
	
	@Test
	fun `inContainer returns true for hostWorkspace itself`() {
		assertTrue(resolver.inContainer(config.workspaceHostPath))
	}
	
	@Test
	fun `inContainer returns false for host path`() {
		val workspace = Path.of("/home/user/projects")
		assertFalse(resolver.inContainer(workspace))
	}
	
	@Test
	fun `inContainer normalizes path before checking`() {
		val workspace = Path.of("/home/user/.config/autotweaker/container/../container/workspace/proj")
		assertTrue(resolver.inContainer(workspace))
	}
	
	// endregion
	
	// region toAbsolutePath
	
	@Test
	fun `toAbsolutePath resolves relative path against container workspace`() {
		val workspace = Path.of("/home/user/.config/autotweaker/container/workspace/proj")
		val result = resolver.toAbsolutePath(workspace, Path.of("src/main.kt"))
		assertEquals(Path.of("/workspace/proj/src/main.kt"), result)
	}
	
	@Test
	fun `toAbsolutePath resolves relative path against host workspace`() {
		val workspace = Path.of("/home/user/projects/myproject")
		val result = resolver.toAbsolutePath(workspace, Path.of("src/main.kt"))
		assertEquals(Path.of("/home/user/projects/myproject/src/main.kt"), result)
	}
	
	@Test
	fun `toAbsolutePath with dot-dot relative path`() {
		val workspace = Path.of("/home/user/.config/autotweaker/container/workspace/proj/sub")
		val result = resolver.toAbsolutePath(workspace, Path.of("../other/file.kt"))
		assertEquals(Path.of("/workspace/proj/other/file.kt"), result)
	}
	
	@Test
	fun `toAbsolutePath with empty relative path returns base`() {
		val workspace = Path.of("/home/user/.config/autotweaker/container/workspace/proj")
		val result = resolver.toAbsolutePath(workspace, Path.of(""))
		assertEquals(Path.of("/workspace/proj"), result)
	}
	
	// endregion
	
	// region toContainerPath
	
	@Test
	fun `toContainerPath maps hostWorkspace to containerWork`() {
		val host = Path.of("/home/user/.config/autotweaker/container/workspace/proj/src")
		val result = resolver.toContainerPath(host)
		assertEquals(Path.of("/workspace/proj/src"), result)
	}
	
	@Test
	fun `toContainerPath maps hostWorkspace root to containerWork root`() {
		val result = resolver.toContainerPath(config.workspaceHostPath)
		assertEquals(Path.of("/workspace"), result)
	}
	
	@Test
	fun `toContainerPath maps hostTmp to containerTmp`() {
		val host = Path.of("/tmp/autotweaker/container/somefile.txt")
		val result = resolver.toContainerPath(host)
		assertEquals(Path.of("/tmp/autotweaker/somefile.txt"), result)
	}
	
	@Test
	fun `toContainerPath throws for path outside both prefixes`() {
		val host = Path.of("/home/user/other/file.txt")
		assertFailsWith<PathOutsideWorkspaceException> {
			resolver.toContainerPath(host)
		}
	}
	
	@Test
	fun `toContainerPath normalizes before checking`() {
		val host = Path.of("/home/user/.config/autotweaker/container/workspace/../workspace/proj")
		val result = resolver.toContainerPath(host)
		assertEquals(Path.of("/workspace/proj"), result)
	}
	
	// endregion
	
	// region toHostPath
	
	@Test
	fun `toHostPath maps containerWork to hostWorkspace`() {
		val container = Path.of("/workspace/proj/src/Main.kt")
		val result = resolver.toHostPath(container)
		assertEquals(
			Path.of("/home/user/.config/autotweaker/container/workspace/proj/src/Main.kt"),
			result
		)
	}
	
	@Test
	fun `toHostPath maps containerWork root to hostWorkspace root`() {
		val result = resolver.toHostPath(Path.of("/workspace"))
		assertEquals(config.workspaceHostPath, result)
	}
	
	@Test
	fun `toHostPath maps containerTmp to hostTmp`() {
		val container = Path.of("/tmp/autotweaker/somefile.txt")
		val result = resolver.toHostPath(container)
		assertEquals(Path.of("/tmp/autotweaker/container/somefile.txt"), result)
	}
	
	@Test
	fun `toHostPath throws for path outside both prefixes`() {
		val container = Path.of("/other/path/file.txt")
		assertFailsWith<PathOutsideWorkspaceException> {
			resolver.toHostPath(container)
		}
	}
	
	@Test
	fun `toHostPath normalizes before checking`() {
		val container = Path.of("/workspace/../workspace/proj")
		val result = resolver.toHostPath(container)
		assertEquals(
			Path.of("/home/user/.config/autotweaker/container/workspace/proj"),
			result
		)
	}
	
	// endregion
	
	// region round-trip
	
	@Test
	fun `toContainerPath then toHostPath returns original path`() {
		val original = Path.of("/home/user/.config/autotweaker/container/workspace/proj/src")
		val roundTrip = resolver.toHostPath(resolver.toContainerPath(original))
		assertEquals(original, roundTrip)
	}
	
	@Test
	fun `toHostPath then toContainerPath returns original path`() {
		val original = Path.of("/workspace/proj/src")
		val roundTrip = resolver.toContainerPath(resolver.toHostPath(original))
		assertEquals(original, roundTrip)
	}
	
	@Test
	fun `round-trip works for tmp paths`() {
		val hostTmp = Path.of("/tmp/autotweaker/container/data.json")
		val roundTrip = resolver.toHostPath(resolver.toContainerPath(hostTmp))
		assertEquals(hostTmp, roundTrip)
	}
	
	// endregion
}
