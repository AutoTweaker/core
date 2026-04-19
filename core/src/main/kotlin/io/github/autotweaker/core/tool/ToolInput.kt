package io.github.autotweaker.core.tool

import kotlinx.serialization.json.JsonObject

abstract class ToolInput {
	abstract val arguments: JsonObject
	abstract val provider: SimpleContainer
}
