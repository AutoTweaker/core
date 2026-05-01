package io.github.autotweaker.core.agent.tool.service

import io.github.autotweaker.core.Unicode
import io.github.autotweaker.core.tool.impl.read.FileSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class FileSystemServiceImpl(
	workspaceRoot: Path,
	private val inContainer: Boolean = false,
	containerRoot: Path? = null,
	hostRoot: Path? = null,
) : FileSystemService {
	private val root: Path = workspaceRoot.toRealPath()
	private val containerMount: Path = containerRoot?.toRealPath() ?: root
	private val hostMount: Path = hostRoot?.toRealPath() ?: root
	
	override fun normalize(filePath: String): Path {
		val path = Paths.get(filePath)
		val resolved = if (path.isAbsolute) path.normalize() else root.resolve(path).normalize()
		if (!inContainer) return resolved
		return hostMount.resolve(containerMount.relativize(resolved)).normalize()
	}
	
	override suspend fun exists(path: Path): Boolean =
		withContext(Dispatchers.IO) { Files.exists(path) }
	
	override suspend fun isRegularFile(path: Path): Boolean =
		withContext(Dispatchers.IO) { Files.isRegularFile(path) }
	
	override suspend fun readUnicode(path: Path): List<Unicode> =
		withContext(Dispatchers.IO) {
			Files.readString(path).map { Unicode.fromChar(it) }
		}
	
	override suspend fun readAllLines(path: Path): List<String> =
		withContext(Dispatchers.IO) { Files.readAllLines(path) }
	
	override suspend fun sha256(path: Path): String =
		withContext(Dispatchers.IO) {
			val digest = MessageDigest.getInstance("SHA-256")
			Files.newInputStream(path).use { input ->
				val buffer = ByteArray(8192)
				var bytesRead: Int
				while (input.read(buffer).also { bytesRead = it } != -1) {
					digest.update(buffer, 0, bytesRead)
				}
			}
			digest.digest().joinToString("") { "%02x".format(it) }
		}
}
