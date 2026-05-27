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
import io.github.autotweaker.core.domain.tool.CoreTool
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.get
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.infrastructure.persistence.EnvStorage
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

@AutoService(CoreTool::class)
class Bash : CoreTool {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val envStorage = EnvStorage(this::class)
	private lateinit var _meta: Tool.Meta
	private lateinit var settings: SettingService
	override val meta: Tool.Meta get() = _meta
	
	override fun init(service: SettingService) {
		settings = service
		
		val envIds = listEnv().sorted().joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" }.ifBlank { "<none>" }
		
		_meta = Tool.Meta(
			name = "bash",
			description = settings.get(BashSettings.Description()).value,
			functions = listOf(
				Tool.Function(
					name = "run",
					description = settings.get(BashSettings.RunFuncDescription()).value,
					parameters = mapOf(
						"command" to Tool.Function.Property(
							description = settings.get(BashSettings.CommandPropDescription()).value,
							required = true,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
						"timeout_seconds" to Tool.Function.Property(
							description = settings.get(BashSettings.TimeoutPropDescription()).value.format(
								settings.get(BashSettings.DefaultTimeoutSeconds()).value
							),
							required = false,
							valueType = Tool.Function.Property.ValueType.IntegerValue(),
						),
						"env_ids" to Tool.Function.Property(
							description = settings.get(BashSettings.EnvIdsPropDescription()).value.format(envIds),
							required = false,
							valueType = Tool.Function.Property.ValueType.ArrayValue(
								Tool.Function.Property.ValueType.StringValue()
							),
						),
					),
				),
			),
		)
	}
	
	override suspend fun coreExec(container: SimpleContainer, input: Tool.ToolInput): Tool.ToolOutput {
		val s = settings
		val command = input.arguments["command"]!!.jsonPrimitive.content
		if (command.isBlank()) {
			logger.debug("Rejected blank bash command  tool=bash")
			return Tool.ToolOutput(s.get(BashSettings.InvalidCommandMessage()).value, false)
		}
		val defaultTimeout = s.get(BashSettings.DefaultTimeoutSeconds()).value
		val timeoutSeconds = input.arguments["timeout_seconds"]?.jsonPrimitive?.int ?: defaultTimeout
		if (timeoutSeconds <= 0) {
			logger.debug("Rejected invalid bash timeout  tool=bash  timeout={}", timeoutSeconds)
			return Tool.ToolOutput(s.get(BashSettings.InvalidTimeoutMessage()).value, false)
		}
		val envIds = input.arguments["env_ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
		val selectedEnv = envIds.mapNotNull { id -> getEnv(id)?.let { id to it } }.toMap()
		
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
		val stdoutStr = stdout.toString().trimEnd().ifBlank { "<empty>" }
		val stderrStr = stderr.toString().trimEnd().ifBlank { "<empty>" }
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
	
	fun listEnv(): List<String> = envStorage.listEnv()
	fun getEnv(id: String): String? = envStorage.getEnv(id)
	fun setEnv(id: String, value: String) = envStorage.setEnv(id, value)
	fun removeEnv(id: String) = envStorage.removeEnv(id)
}
