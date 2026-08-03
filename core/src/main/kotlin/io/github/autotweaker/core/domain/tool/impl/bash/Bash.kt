/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.core.domain.tool.impl.bash

import com.google.auto.service.AutoService
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.generated.tool.args.BashArgs
import io.github.autotweaker.api.get
import io.github.autotweaker.api.log
import io.github.autotweaker.api.tool.Ready
import io.github.autotweaker.api.tool.Rejected
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolResult
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.tool.bash.BashOutput
import io.github.autotweaker.api.types.tool.bash.BashRequest
import io.github.autotweaker.api.types.tool.bash.BashResult
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.TruncationService
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@AutoService(CoreTool::class)
class Bash : CoreTool<BashArgs>, Loggable {
	override suspend fun meta() = bashMeta(
		BashMetaDescriptions(
			toolDescription = BashDesc.Tool().get(),
			functions = BashMetaDescriptions.Functions(
				run = BashMetaDescriptions.Functions.Run(
					command = BashDesc.Command().get(),
					timeoutSeconds = BashDesc.Timeout().get()
						.format(BashSettings.DefaultTimeoutSeconds().get()),
					envIds = BashDesc.EnvIds().get().format(envListString()),
				) to BashDesc.Function().get(),
			)
		)
	)
	
	private suspend fun envListString(): String =
		listEnv().sorted().let { if (it.isEmpty()) "[none]" else Json.encodeToString(it) }
	
	override suspend fun coreResolve(dependency: DependencyProvider, args: BashArgs): Tool.ResolveResult {
		val request = args as BashArgs.Run
		val command = request.command
		if (command.isBlank()) return Rejected(BashMessage.InvalidCommand())
		
		val timeoutSeconds = request.timeoutSeconds ?: BashSettings.DefaultTimeoutSeconds().get()
		if (timeoutSeconds <= 0) return Rejected(BashMessage.InvalidTimeout())
		
		val envIds = request.envIds.toSet()
		val notFound = envIds - listEnv().toSet()
		if (notFound.isNotEmpty()) return Rejected(BashMessage.EnvNotFound(), notFound)
		
		return Ready(
			BashRequest.serializer(), BashRequest(
				command = command,
				timeout = timeoutSeconds.seconds,
				envIds = envIds
			)
		)
	}
	
	override suspend fun coreExec(
		dependency: DependencyProvider,
		request: JsonElement,
		outputChannel: Channel<Tool.RuntimeOutput>
	): Tool.ToolOutput {
		val request = Json.decodeFromJsonElement(BashRequest.serializer(), request)
		val selectedEnv = request.envIds.mapNotNull { id -> getEnv(id)?.let { id to it } }.toMap()
		
		log.debug(
			"Started bash execution  tool=bash  commandPreview={}  timeout={}",
			request.command.take(50), request.timeout
		)
		
		val lines = mutableListOf<BashOutput>()
		var exit: ShellEvent.Exit? = null
		
		dependency.get<BashService>().run(request.command, request.timeout, selectedEnv).collect { event ->
			when (event) {
				is ShellEvent.Stdout -> {
					outputChannel.send(Tool.RuntimeOutput(event.text, Tool.RuntimeOutput.OutputType.INFO))
					lines.add(BashOutput.Stdout(event.text))
				}
				
				is ShellEvent.Stderr -> {
					outputChannel.send(Tool.RuntimeOutput(event.text, Tool.RuntimeOutput.OutputType.ERROR))
					lines.add(BashOutput.Stderr(event.text))
				}
				
				is ShellEvent.Exit -> exit = event
			}
		}
		
		val result = checkNotNull(exit) { "Bash completed with no exit event" }.result
		val duration = String.format("%.3f", result.duration.toDouble(DurationUnit.SECONDS))
		
		log.debug(
			"Completed bash  tool=bash  exitCode={}  duration={}s  timeout={}",
			result.exitCode,
			duration,
			result.timeout
		)
		
		val stdout = lines.filterOutput<BashOutput.Stdout>()
		val stderr = lines.filterOutput<BashOutput.Stderr>()
		
		fun processOutput(content: String) =
			dependency.get<TruncationService>()(
				content = content.trimEnd().ifBlank { "[empty]" },
				threshold = BashSettings.MaxOutput().get(),
				keepTail = true
			)
		
		val success = result.exitCode == 0 && !result.timeout
		val output = BashResult(
			lines, result.exitCode, result.timeout, result.duration
		)
		
		return BashMessage.ToolResult().get()
			.format(result.exitCode, duration, processOutput(stdout), processOutput(stderr))
			.toolResult(output, BashResult.serializer(), success)
	}
	
	private inline fun <reified T : BashOutput> List<BashOutput>.filterOutput() =
		filterIsInstance<T>().joinToString("\n") { it.content }
	
	companion object : EnvStore()
}
