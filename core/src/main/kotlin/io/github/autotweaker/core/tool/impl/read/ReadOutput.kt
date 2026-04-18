package io.github.autotweaker.core.tool.impl.read

import io.github.autotweaker.core.tool.ToolOutput

data class ReadOutput(
    override val result: String,
    override val success: Boolean,
) : ToolOutput()
