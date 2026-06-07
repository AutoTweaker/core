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
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.infrastructure.persistence.EnvStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.reflect.KProperty1
import kotlin.time.Duration.Companion.seconds

@AutoService(CoreTool::class)
class Bash : CoreTool<Bash.Args> {
	@Serializable
	data class Args(
		val command: String,
		val timeoutSeconds: Int = 60,
		val envIds: List<String> = emptyList(),
	)
	
	private val logger = LoggerFactory.getLogger(this::class.java)
	private lateinit var envStorage: EnvStorage
	private lateinit var settings: SettingService
	
	override val argsSerializer = Args.serializer()
	override val name = "bash"
	override val description get() = settings.get(BashSettings.Description()).value
	
	override suspend fun describe(): Map<KProperty1<*, *>, String> {
		val envIds = listEnv().sorted().let { if (it.isEmpty()) "[none]" else Json.encodeToString(it) }
		return mapOf(
			Args::command to settings.get(BashSettings.CommandPropDescription()).value,
			Args::timeoutSeconds to settings.get(BashSettings.TimeoutPropDescription()).value.format(
				settings.get(BashSettings.DefaultTimeoutSeconds()).value
			),
			Args::envIds to settings.get(BashSettings.EnvIdsPropDescription()).value.format(envIds),
		)
	}
	
	override suspend fun init(service: SettingService, secretStore: SecretStore) {
		envStorage = EnvStorage(this::class, secretStore)
		settings = service
	}
	
	override suspend fun coreExec(container: SimpleContainer, input: Tool.ToolInput<Args>): Tool.ToolOutput {
		val s = settings
		val args = input.args
		val command = args.command
		if (command.isBlank()) {
			logger.debug("Rejected blank bash command  tool=bash")
			return Tool.ToolOutput(s.get(BashSettings.InvalidCommandMessage()).value, false)
		}
		val timeoutSeconds = args.timeoutSeconds
		if (timeoutSeconds <= 0) {
			logger.debug("Rejected invalid bash timeout  tool=bash  timeout={}", timeoutSeconds)
			return Tool.ToolOutput(s.get(BashSettings.InvalidTimeoutMessage()).value, false)
		}
		val selectedEnv = args.envIds.mapNotNull { id -> getEnv(id)?.let { id to it } }.toMap()
		
		logger.debug(
			"Bash execution started  tool=bash  commandPreview={}  timeout={}s", command.take(100), timeoutSeconds
		)
		
		val stdout = StringBuilder()
		val stderr = StringBuilder()
		var result: ShellEvent.Exit? = null
		
		container.get<BashService>().run(command, timeoutSeconds.seconds, selectedEnv).collect { event ->
			when (event) {
				is ShellEvent.Stdout -> {
					input.outputChannel?.send(Tool.RuntimeOutput(event.text))
					stdout.appendLine(event.text)
				}
				
				is ShellEvent.Stderr -> {
					input.outputChannel?.send(Tool.RuntimeOutput(event.text))
					stderr.appendLine(event.text)
				}
				
				is ShellEvent.Exit -> result = event
			}
		}
		
		val r = result ?: return Tool.ToolOutput("No result", false)
		val stdoutStr = stdout.toString().trimEnd().ifBlank { "[empty]" }
		val stderrStr = stderr.toString().trimEnd().ifBlank { "[empty]" }
		val duration = String.format("%.3f", r.result.duration.inWholeMicroseconds / 1_000_000.0)
		
		logger.debug(
			"Bash completed  tool=bash  exitCode={}  duration={}s  timeout={}",
			r.result.exitCode,
			duration,
			r.result.timeout
		)
		
		val output =
			s.get(BashSettings.ResultTemplate()).value.format(r.result.exitCode, duration, stdoutStr, stderrStr)
		return Tool.ToolOutput(output, r.result.exitCode == 0 && !r.result.timeout)
	}
	
	suspend fun listEnv(): List<String> = envStorage.listEnv()
	suspend fun getEnv(id: String): String? = envStorage.getEnv(id)
	suspend fun setEnv(id: String, value: String) = envStorage.setEnv(id, value)
	suspend fun removeEnv(id: String) = envStorage.removeEnv(id)
}
