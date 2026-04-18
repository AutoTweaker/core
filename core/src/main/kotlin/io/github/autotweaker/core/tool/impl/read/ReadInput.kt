package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.tool.DependencyProvider
import io.github.autotweaker.core.tool.ToolInput
import kotlinx.serialization.json.JsonObject

data class ReadInput(
    override val arguments: JsonObject,
    override val provider: DependencyProvider,
    val previousReads: List<PreviousRead>,
) : ToolInput() {
    data class PreviousRead(
        val filePath: String,
        val fileSha256: String,
        val startLine: Int,
        val endLine: Int,
    )
}
