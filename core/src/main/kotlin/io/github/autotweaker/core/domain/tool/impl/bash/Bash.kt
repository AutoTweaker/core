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
import io.github.autotweaker.api.*
import io.github.autotweaker.api.generated.tool.args.BashArgs
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolFail
import io.github.autotweaker.api.tool.toolResult
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.TruncationService
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@AutoService(CoreTool::class)
class Bash : CoreTool<BashArgs>, Loggable, Settable {
	override suspend fun meta() = bashMeta(
		BashMetaDescriptions(
			toolDescription = setting(BashSettings.Description()),
			functions = BashMetaDescriptions.Functions(
				run = BashMetaDescriptions.Functions.Run(
					command = setting(BashSettings.CommandPropDescription()),
					timeoutSeconds = setting(BashSettings.TimeoutPropDescription())
						.format(setting(BashSettings.DefaultTimeoutSeconds())),
					envIds = setting(BashSettings.EnvIdsPropDescription()).format(envListString()),
				) to setting(BashSettings.RunFuncDescription()),
			)
		)
	)
	
	private suspend fun envListString(): String =
		listEnv().sorted().let { if (it.isEmpty()) "[none]" else Json.encodeToString(it) }
	
	override suspend fun coreExec(
		container: DependencyProvider,
		args: BashArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		val request = args as BashArgs.Run
		val command = request.command
		if (command.isBlank()) return setting(BashSettings.InvalidCommandMessage()).toolFail()
			.andLog(log) { debug("Rejected blank bash command  tool=bash") }
		
		val timeoutSeconds = request.timeoutSeconds ?: setting(BashSettings.DefaultTimeoutSeconds())
		if (timeoutSeconds <= 0) return setting(BashSettings.InvalidTimeoutMessage()).toolFail()
			.andLog(log) {
				debug("Rejected invalid bash timeout  tool=bash  timeout={}", timeoutSeconds)
			}
		
		val selectedEnv = request.envIds.mapNotNull { id -> getEnv(id)?.let { id to it } }.toMap()
		
		log.debug(
			"Started bash execution  tool=bash  commandPreview={}  timeout={}s",
			command.take(100), timeoutSeconds
		)
		
		val stdout = StringBuilder()
		val stderr = StringBuilder()
		var result: ShellEvent.Exit? = null
		
		container.get<BashService>().run(command, timeoutSeconds.seconds, selectedEnv).collect { event ->
			when (event) {
				is ShellEvent.Stdout -> {
					outputChannel?.send(Tool.RuntimeOutput(event.text, Tool.RuntimeOutput.OutputType.INFO))
					stdout.appendLine(event.text)
				}
				
				is ShellEvent.Stderr -> {
					outputChannel?.send(Tool.RuntimeOutput(event.text, Tool.RuntimeOutput.OutputType.ERROR))
					stderr.appendLine(event.text)
				}
				
				is ShellEvent.Exit -> result = event
			}
		}
		
		fun processOutput(content: StringBuilder) =
			container.get<TruncationService>()(
				content = content.toString().trimEnd().ifBlank { "[empty]" },
				threshold = setting(BashSettings.MaxOutput()),
				keepTail = true
			)
		
		val r = result ?: return "No result".toolFail()
		val duration = String.format("%.3f", r.result.duration.toDouble(DurationUnit.SECONDS))
		
		log.debug(
			"Completed bash  tool=bash  exitCode={}  duration={}s  timeout={}",
			r.result.exitCode,
			duration,
			r.result.timeout
		)
		
		return setting(BashSettings.ResultTemplate())
			.format(r.result.exitCode, duration, processOutput(stdout), processOutput(stderr))
			.toolResult(r.result.exitCode == 0 && !r.result.timeout)
	}
	
	companion object : EnvStore()
}
