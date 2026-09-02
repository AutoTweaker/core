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

package io.github.autotweaker.core.infrastructure.system

import com.google.common.hash.Hasher
import com.google.common.hash.Hashing
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.CatchingResult
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.core.domain.port.FileContent
import io.github.autotweaker.core.domain.port.FileMetadata
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.exception.*
import io.github.autotweaker.core.domain.port.exception.FileAlreadyExistsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserDefinedFileAttributeView
import kotlin.time.toKotlinInstant
import java.nio.file.FileAlreadyExistsException as JvmFileAlreadyExistsException

object RawFileSystemImpl : RawFileSystem, Loggable, Traceable {
	private val pathLocks = Array(256) { Mutex() }
	private val ownerOnly = PosixFilePermissions.fromString("rw-------")
	private const val MAX_READ_CHARS = 10 * 1024 * 1024
	private const val BUFFER_SIZE = 8192
	override suspend fun exists(path: Path): Boolean = withContext(Dispatchers.IO) {
		Files.exists(path)
	}
	
	override suspend fun isRegularFile(path: Path): Boolean = withContext(Dispatchers.IO) {
		Files.isRegularFile(path)
	}
	
	override suspend fun metadata(path: Path): FileMetadata = withContext(Dispatchers.IO) {
		trace.catching {
			with(Files.readAttributes(path, PosixFileAttributes::class.java)) {
				FileMetadata(
					size = size(),
					lastModifiedTime = lastModifiedTime().toInstant().toKotlinInstant(),
					lastAccessTime = lastAccessTime().toInstant().toKotlinInstant(),
					creationTime = creationTime().toInstant().toKotlinInstant(),
					isRegularFile = isRegularFile,
					isDirectory = isDirectory,
					isSymbolicLink = isSymbolicLink,
					isOther = isOther,
					fileKey = fileKey()?.toString(),
					owner = owner().name,
					group = group().name,
					permissions = permissions(),
				)
			}
		}.rethrowFileSystemException()
	}
	
	override suspend fun lineCount(path: Path): Int = withContext(Dispatchers.IO) {
		trace.catching {
			Files.newInputStream(path).use { input ->
				var count = 0
				var last = -1
				val buffer = ByteArray(BUFFER_SIZE)
				while (true) {
					val read = input.read(buffer)
					if (read < 0) break
					for (i in 0 until read) {
						if (buffer[i] == '\n'.code.toByte()) count++
						last = buffer[i].toInt()
					}
				}
				if (last != -1 && last != '\n'.code) count++
				return@use count
			}
		}.rethrowFileSystemException()
	}
	
	@Suppress("UnstableApiUsage")
	override suspend fun read(path: Path): FileContent = withContext(Dispatchers.IO) {
		trace.catching {
			Files.newInputStream(path).use { input ->
				val hasher = Hashing.sha256().newHasher()
				val hashing = HashingInputStream(input, hasher)
				val reader = InputStreamReader(hashing, Charsets.UTF_8)
				val initialSize = minOf(
					maxOf(Files.size(path), BUFFER_SIZE.toLong()),
					MAX_READ_CHARS.toLong()
				).toInt()
				var chars = CharArray(initialSize)
				var total = 0
				while (total < chars.size) {
					val read = reader.read(chars, total, chars.size - total)
					if (read < 0) break
					total += read
					if (total == chars.size && total < MAX_READ_CHARS) {
						chars = chars.copyOf(minOf(chars.size * 2, MAX_READ_CHARS))
					}
				}
				val truncated = total == MAX_READ_CHARS && reader.read() != -1
				if (truncated) {
					val buffer = ByteArray(BUFFER_SIZE)
					while (true) {
						val read = hashing.read(buffer)
						if (read < 0) break
					}
				}
				FileContent(
					String(chars, 0, total),
					truncated,
					Sha256(hasher.hash()),
				)
			}
		}.rethrowFileSystemException()
	}
	
	@Suppress("UnstableApiUsage")
	override suspend fun sha256(path: Path): Sha256 = withContext(Dispatchers.IO) {
		trace.catching {
			val hasher = Hashing.sha256().newHasher()
			Files.newInputStream(path).use { input ->
				val buffer = ByteArray(BUFFER_SIZE)
				while (true) {
					val read = input.read(buffer)
					if (read < 0) break
					hasher.putBytes(buffer, 0, read)
				}
			}
			Sha256(hasher.hash())
		}.rethrowFileSystemException()
	}
	
	override suspend fun create(path: Path, content: String): Sha256 = withContext(Dispatchers.IO) {
		val target = path.toAbsolutePath()
		val bytes = content.toByteArray()
		trace.catching {
			pathLocks[target.hashCode() and 255].withLock {
				target.parent?.let { Files.createDirectories(it) }
				var created = false
				trace.catching {
					FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
						.use { channel ->
							created = true
							trace.catching { Files.setPosixFilePermissions(target, ownerOnly) }
							val buffer = ByteBuffer.wrap(bytes)
							while (buffer.hasRemaining()) channel.write(buffer)
							channel.force(true)
						}
				}.also { result ->
					if (created && result.isFailure) trace.catching { Files.deleteIfExists(target) }
				}.getOrThrow()
				Sha256(Hashing.sha256().hashBytes(bytes))
			}
		}.also { target.parent?.let { fsyncDir(it) } }
			.rethrowFileSystemException()
	}
	
	override suspend fun update(path: Path, expected: Sha256, new: String): Sha256 =
		withContext(Dispatchers.IO) {
			trace.catching {
				val target = path.toRealPath()
				if (!Files.isWritable(target)) throw FileNotWritableException()
				pathLocks[target.hashCode() and 255].withLock {
					val current = sha256(target)
					if (current != expected) throw FileChangedException()
					atomicReplace(target, new)
				}
			}.rethrowFileSystemException()
		}
	
	private fun atomicReplace(path: Path, new: String): Sha256 {
		val tmp = path.resolveSibling(".${path.fileName}.${UUID()}.tmp")
		val hash = trace.catching {
			val bytes = new.toByteArray()
			FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
				.use { channel ->
					trace.catching { Files.setPosixFilePermissions(tmp, ownerOnly) }
					val buffer = ByteBuffer.wrap(bytes)
					while (buffer.hasRemaining()) channel.write(buffer)
					channel.force(true)
				}
			copyMetadata(path, tmp)
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			Sha256(Hashing.sha256().hashBytes(bytes))
		}.also { result ->
			if (result.isFailure) trace.catching { Files.deleteIfExists(tmp) }
		}.rethrowCancellation()
			.onFailure { log.error("Failed to write file  path={}  reason={}", path, it.message, it) }
			.getOrThrow()
		path.parent?.let { fsyncDir(it) }
		return hash
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
	
	override suspend fun list(path: Path): List<Path> = withContext(Dispatchers.IO) {
		trace.catching {
			Files.list(path).use { stream -> stream.toList() }
		}.rethrowFileSystemException()
	}
	
	override suspend fun glob(pattern: String, cwd: Path): List<Path> =
		withContext(Dispatchers.IO) {
			val matcher = cwd.fileSystem.getPathMatcher("glob:$pattern")
			Files.walk(cwd).use { stream ->
				stream.filter { matcher.matches(it) }.toList()
			}
		}
	
	private fun <T> CatchingResult<T>.rethrowFileSystemException(): T =
		rethrowCancellation()
			.recoverException { e: AccessDeniedException -> throw FileAccessDeniedException(e) }
			.recoverException { e: JvmFileAlreadyExistsException -> throw FileAlreadyExistsException(e) }
			.recoverException { e: NoSuchFileException -> throw FileNotFoundException(e) }
			.getOrThrow()
	
	@Suppress("UnstableApiUsage")
	private class HashingInputStream(
		input: InputStream,
		private val hasher: Hasher,
	) : FilterInputStream(input) {
		override fun read(): Int {
			val b = super.read()
			if (b >= 0) hasher.putByte(b.toByte())
			return b
		}
		
		override fun read(b: ByteArray, off: Int, len: Int): Int {
			val n = super.read(b, off, len)
			if (n > 0) hasher.putBytes(b, off, n)
			return n
		}
	}
}
