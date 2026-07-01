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
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.tool.args.BashArgs
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.DependencyProvider
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.TruncationService
import io.github.autotweaker.core.infrastructure.persist.json.EnvStore
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlin.reflect.KProperty1
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@AutoService(CoreTool::class)
class Bash : CoreTool<BashArgs>, Loggable, Settable {
	override val argsSerializer = BashArgs.serializer()
	override val name = "bash"
	override val description get() = setting(BashSettings.Description())
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> {
		val envIds = listEnv().sorted().let { if (it.isEmpty()) "[none]" else Json.encodeToString(it) }
		return mapOf(
			BashArgs::command to setting(BashSettings.CommandPropDescription()),
			BashArgs::timeoutSeconds to setting(BashSettings.TimeoutPropDescription()).format(
				setting(BashSettings.DefaultTimeoutSeconds())
			),
			BashArgs::envIds to setting(BashSettings.EnvIdsPropDescription()).format(envIds),
		)
	}
	
	override suspend fun coreExec(
		container: DependencyProvider,
		args: BashArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		val command = args.command
		if (command.isBlank()) return Tool.ToolOutput(
			setting(BashSettings.InvalidCommandMessage()), false
		).andLog(log) { debug("Rejected blank bash command  tool=bash") }
		
		val timeoutSeconds = args.timeoutSeconds
		if (timeoutSeconds <= 0) return Tool.ToolOutput(
			setting(BashSettings.InvalidTimeoutMessage()), false
		).andLog(log) {
			debug("Rejected invalid bash timeout  tool=bash  timeout={}", timeoutSeconds)
		}
		
		val selectedEnv = args.envIds.mapNotNull { id -> getEnv(id)?.let { id to it } }.toMap()
		
		log.debug(
			"Started bash execution  tool=bash  commandPreview={}  timeout={}s", command.take(100), timeoutSeconds
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
		
		val r = result ?: return Tool.ToolOutput("No result", false)
		val duration = String.format("%.3f", r.result.duration.toDouble(DurationUnit.SECONDS))
		
		log.debug(
			"Completed bash  tool=bash  exitCode={}  duration={}s  timeout={}",
			r.result.exitCode,
			duration,
			r.result.timeout
		)
		
		val output = setting(BashSettings.ResultTemplate())
			.format(r.result.exitCode, duration, processOutput(stdout), processOutput(stderr))
		return Tool.ToolOutput(output, r.result.exitCode == 0 && !r.result.timeout)
	}
	
	companion object : EnvStore()
}
