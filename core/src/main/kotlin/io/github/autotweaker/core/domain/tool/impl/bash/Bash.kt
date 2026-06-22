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
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.infrastructure.persistence.EnvStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlin.reflect.KProperty1
import kotlin.time.Duration.Companion.seconds

@AutoService(CoreTool::class)
class Bash : CoreTool<BashArgs>, Loggable, JsonStorable, Settable {
	private lateinit var envStorage: EnvStorage
	
	override val argsSerializer = BashArgs.serializer()
	override val name = "bash"
	override val description get() = setting.get(BashSettings.Description()).value
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> {
		val envIds = listEnv().sorted().let { if (it.isEmpty()) "[none]" else Json.encodeToString(it) }
		return mapOf(
			BashArgs::command to setting.get(BashSettings.CommandPropDescription()).value,
			BashArgs::timeoutSeconds to setting.get(BashSettings.TimeoutPropDescription()).value.format(
				setting.get(BashSettings.DefaultTimeoutSeconds()).value
			),
			BashArgs::envIds to setting.get(BashSettings.EnvIdsPropDescription()).value.format(envIds),
		)
	}
	
	override suspend fun init(secretStore: SecretStore) {
		envStorage = EnvStorage(this::class, store, secretStore)
	}
	
	override suspend fun coreExec(
		container: SimpleContainer,
		args: BashArgs,
		outputChannel: Channel<Tool.RuntimeOutput>?
	): Tool.ToolOutput {
		val command = args.command
		if (command.isBlank()) {
			log.debug("Rejected blank bash command  tool=bash")
			return Tool.ToolOutput(setting.get(BashSettings.InvalidCommandMessage()).value, false)
		}
		val timeoutSeconds = args.timeoutSeconds
		if (timeoutSeconds <= 0) {
			log.debug("Rejected invalid bash timeout  tool=bash  timeout={}", timeoutSeconds)
			return Tool.ToolOutput(setting.get(BashSettings.InvalidTimeoutMessage()).value, false)
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
		
		val r = result ?: return Tool.ToolOutput("No result", false)
		val stdoutStr = stdout.toString().trimEnd().ifBlank { "[empty]" }
		val stderrStr = stderr.toString().trimEnd().ifBlank { "[empty]" }
		val duration = String.format("%.3f", r.result.duration.inWholeMicroseconds / 1_000_000.0)
		
		log.debug(
			"Completed bash  tool=bash  exitCode={}  duration={}s  timeout={}",
			r.result.exitCode,
			duration,
			r.result.timeout
		)
		
		val output =
			setting.get(BashSettings.ResultTemplate()).value.format(r.result.exitCode, duration, stdoutStr, stderrStr)
		return Tool.ToolOutput(output, r.result.exitCode == 0 && !r.result.timeout)
	}
	
	suspend fun listEnv(): List<String> = envStorage.listEnv()
	suspend fun getEnv(id: String): String? = envStorage.getEnv(id)
	suspend fun setEnv(id: String, value: String) = envStorage.setEnv(id, value)
	suspend fun removeEnv(id: String) = envStorage.removeEnv(id)
}
