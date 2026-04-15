package io.github.whiteelephant.autotweaker.core.data.json.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ConfigFileStore(filePath: String) {
    private val file: File = File(filePath)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun <T> write(value: T, serializer: KSerializer<T>): Unit = synchronized(lock) {
        file.parentFile?.mkdirs()
        val path = file.toPath()
        val tmp = Files.createTempFile(path.parent, ".", ".tmp")
        try {
            Files.writeString(tmp, json.encodeToString(serializer, value))
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    fun <T> read(serializer: KSerializer<T>): T? = synchronized(lock) {
        if (!file.exists()) return null
        return json.decodeFromString(serializer, file.readText())
    }
}
