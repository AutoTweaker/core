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

package io.github.autotweaker.core.infrastructure.tool

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.log
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.core.domain.port.RawFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserDefinedFileAttributeView
import java.util.*

object RawFileSystemImpl : RawFileSystem, Loggable, Traceable {
	private val pathLocks = Array(256) { Mutex() }
	private val ownerOnly = PosixFilePermissions.fromString("rw-------")
	override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
		Files.exists(path)
	}
	
	override suspend fun isRegularFile(path: Path): Boolean = withContext(Dispatchers.IO) {
		Files.isRegularFile(path)
	}
	
	override suspend fun readString(path: Path): String = withContext(Dispatchers.IO) {
		Files.readString(path)
	}
	
	override suspend fun readAllLines(path: Path): List<String> = withContext(Dispatchers.IO) {
		Files.readAllLines(path)
	}
	
	override suspend fun sha256(path: Path): Sha256 = withContext(Dispatchers.IO) {
		Sha256.hash(Files.readAllBytes(path))
	}
	
	override suspend fun write(path: Path, expected: List<String>, lines: List<String>) =
		withContext(Dispatchers.IO) {
			val target = path.toRealPath()
			if (!Files.isWritable(target)) error("File is not writable: $path")
			pathLocks[target.hashCode() and 255].withLock {
				val current = Files.readAllLines(target)
				if (current != expected) error("File content changed since read: $path")
				atomicReplace(target, lines)
			}
		}
	
	private fun atomicReplace(path: Path, lines: List<String>) {
		val tmp = path.resolveSibling(".${path.fileName}.${UUID.randomUUID()}.tmp")
		trace.catching {
			FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
				.use { channel ->
					trace.catching { Files.setPosixFilePermissions(tmp, ownerOnly) }
					channel.write(ByteBuffer.wrap(lines.joinToString("\n").toByteArray()))
					channel.force(true)
				}
			copyMetadata(path, tmp)
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		}.also { result ->
			if (result.isFailure) trace.catching { Files.deleteIfExists(tmp) }
		}.rethrowCancellation()
			.onFailure { log.error("Failed to write file  path={}  reason={}", path, it.message, it) }
			.getOrThrow()
		path.parent?.let { fsyncDir(it) }
	}
	
	private fun copyMetadata(from: Path, to: Path) {
		trace.catching { ProcessBuilder("chmod", "--reference=$from", "$to").start().waitFor() }
		trace.catching { ProcessBuilder("chown", "--reference=$from", "$to").start().waitFor() }
		trace.catching { ProcessBuilder("chcon", "--reference=$from", "$to").start().waitFor() }
		trace.catching {
			val acl = Files.getFileAttributeView(from, AclFileAttributeView::class.java)?.acl
				?: return@catching
			Files.getFileAttributeView(to, AclFileAttributeView::class.java)?.acl = acl
		}
		trace.catching {
			val fromView = Files.getFileAttributeView(from, UserDefinedFileAttributeView::class.java)
				?: return@catching
			val toView = Files.getFileAttributeView(to, UserDefinedFileAttributeView::class.java)
				?: return@catching
			for (name in fromView.list()) {
				val size = fromView.size(name)
				if (size <= 0) continue
				val buffer = ByteBuffer.allocate(size)
				fromView.read(name, buffer)
				buffer.flip()
				toView.write(name, buffer)
			}
		}
	}
	
	private fun fsyncDir(dir: Path) {
		trace.catching {
			FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
		}.onFailure { log.warn("Failed to fsync directory  path={}  reason={}", dir, it.message) }
	}
	
	override suspend fun glob(pattern: String, cwd: Path): List<Path> =
		withContext(Dispatchers.IO) {
			val matcher = cwd.fileSystem.getPathMatcher("glob:$pattern")
			Files.walk(cwd).use { stream ->
				stream.filter { matcher.matches(it) }.toList()
			}
		}
}
