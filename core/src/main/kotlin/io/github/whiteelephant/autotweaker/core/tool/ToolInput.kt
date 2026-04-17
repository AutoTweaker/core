package io.github.whiteelephant.autotweaker.core.tool

import io.github.whiteelephant.autotweaker.core.data.settings.SettingItem
import kotlinx.serialization.json.JsonObject

abstract class ToolInput {
    abstract val arguments: JsonObject
    abstract val settings: List<SettingItem>
    abstract val provider: DependencyProvider
}
