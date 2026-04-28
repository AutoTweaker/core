package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.Unicode
import java.nio.file.Path

interface FileSystemService {
	fun normalize(filePath: String): Path
	suspend fun exists(path: Path): Boolean
	suspend fun isRegularFile(path: Path): Boolean
	suspend fun readUnicode(path: Path): List<Unicode>
	suspend fun readAllLines(path: Path): List<String>
	suspend fun sha256(path: Path): String
}