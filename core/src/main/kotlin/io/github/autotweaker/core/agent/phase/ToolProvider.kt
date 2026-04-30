package io.github.autotweaker.core.agent.phase

import io.github.autotweaker.core.agent.AgentEnvironment
import io.github.autotweaker.core.agent.tool.service.FileSystemServiceImpl
import io.github.autotweaker.core.agent.tool.service.SummarizeServiceImpl
import io.github.autotweaker.core.agent.tool.service.ToolCallHistoryImpl
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.impl.read.FileSystemService
import io.github.autotweaker.core.tool.impl.read.SummarizeService
import io.github.autotweaker.core.tool.impl.read.ToolCallHistory

internal fun buildToolProvider(env: AgentEnvironment): SimpleContainer {
	val container = SimpleContainer()
	val workspace = env.workspace
	val config = env.containerConfig
	container.register(
		FileSystemService::class,
		FileSystemServiceImpl(workspace.path, workspace.inContainer, config.workDir, config.workspaceHostPath),
	)
	container.register(
		SummarizeService::class,
		SummarizeServiceImpl(env.summarizeModel, env.currentFallbackModels),
	)
	container.register(ToolCallHistory::class, ToolCallHistoryImpl(env.context))
	return container
}
