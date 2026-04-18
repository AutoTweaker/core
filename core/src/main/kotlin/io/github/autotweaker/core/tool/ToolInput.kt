package io.github.autotweaker.core.tool

import io.github.autotweaker.core.data.settings.SettingItem
import kotlinx.serialization.json.JsonObject

abstract class ToolInput {
    abstract val arguments: JsonObject
    abstract val provider: SimpleContainer
}
