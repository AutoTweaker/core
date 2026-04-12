package io.github.whiteelephant.autotweaker.core.data.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolute

class ConfigFileStore(filePath: String) {
    private val file = File(Path(filePath).absolute().toString())
    private val json = Json { ignoreUnknownKeys = true }

    fun <T> write(value: T, serializer: KSerializer<T>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, value))
    }

    fun <T> read(serializer: KSerializer<T>): T? {
        if (!file.exists()) return null
        return json.decodeFromString(serializer, file.readText())
    }
}
