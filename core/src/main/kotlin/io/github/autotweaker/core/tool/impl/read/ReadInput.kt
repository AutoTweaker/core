package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.ToolInput
import kotlinx.serialization.json.JsonObject

data class ReadInput(
    override val arguments: JsonObject,
    override val provider: SimpleContainer,
    val previousReads: List<PreviousRead>,
) : ToolInput() {
    data class PreviousRead(
        val filePath: java.nio.file.Path,
        val fileSha256: String,
        val startLine: Int,
        val endLine: Int,
    )
}
