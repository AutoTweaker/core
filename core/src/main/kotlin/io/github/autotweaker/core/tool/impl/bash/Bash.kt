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

package io.github.autotweaker.core.tool.impl.bash

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.core.data.EnvStorage
import io.github.autotweaker.core.tool.Tool
import io.github.autotweaker.core.tool.get
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

@AutoService(Tool::class)
class Bash : Tool {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val envStorage = EnvStorage(this::class)
	
	override fun resolveMeta(service: SettingService): Tool.Meta {
		val envIds = listEnv().sorted().joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" }.ifBlank { "<none>" }
		return Tool.Meta(
			name = "bash",
			description = service.get(BashSettings.Description()).value,
			functions = listOf(
				Tool.Function(
					name = "run",
					description = service.get(BashSettings.RunFuncDescription()).value,
					parameters = mapOf(
						"command" to Tool.Function.Property(
							description = service.get(BashSettings.CommandPropDescription()).value,
							required = true,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
						"timeout_seconds" to Tool.Function.Property(
							description = service.get(BashSettings.TimeoutPropDescription()).value.format(
								service.get(
									BashSettings.DefaultTimeoutSeconds()
								).value
							),
							required = false,
							valueType = Tool.Function.Property.ValueType.IntegerValue(),
						),
						"env_ids" to Tool.Function.Property(
							description = service.get(BashSettings.EnvIdsPropDescription()).value.format(envIds),
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
	
	override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
		val s = input.service
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
		
		val result = input.provider.get<BashService>().run(command, timeoutSeconds, selectedEnv)
		val stdout = result.stdout.ifBlank { "<empty>" }
		val stderr = result.stderr.ifBlank { "<empty>" }
		val duration = String.format("%.3f", result.durationSeconds)
		
		logger.debug(
			"Bash completed  tool=bash  exitCode={}  duration={}s  timeout={}",
			result.exitCode,
			duration,
			result.timeout
		)
		
		val output = s.get(BashSettings.ResultTemplate()).value.format(result.exitCode, duration, stdout, stderr)
		return Tool.ToolOutput(output, result.exitCode == 0 && !result.timeout)
	}
	
	fun listEnv(): List<String> = envStorage.listEnv()
	
	fun getEnv(id: String): String? = envStorage.getEnv(id)
	
	fun setEnv(id: String, value: String) = envStorage.setEnv(id, value)
	
	fun removeEnv(id: String) = envStorage.removeEnv(id)
}
