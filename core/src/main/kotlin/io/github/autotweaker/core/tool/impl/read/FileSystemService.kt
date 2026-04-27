package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.Unicode
import java.nio.file.Path

interface FileSystemService {
	fun normalize(filePath: String): Path
	fun exists(path: Path): Boolean
	fun isRegularFile(path: Path): Boolean
	fun readUnicode(path: Path): List<Unicode>
	fun readAllLines(path: Path): List<String>
	fun sha256(path: Path): String
}