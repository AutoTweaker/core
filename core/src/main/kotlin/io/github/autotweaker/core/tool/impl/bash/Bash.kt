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
import io.github.autotweaker.api.types.settings.SettingItem
import io.github.autotweaker.api.types.settings.find
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
	private val envStorage = EnvStorage(this::class.java.name)
	
	override fun resolveMeta(settings: List<SettingItem>): Tool.Meta {
		val description: String = settings.find("core.tool.bash.description")
		val runDescription: String = settings.find("core.tool.bash.function.description.run")
		val commandDescription: String = settings.find("core.tool.bash.property.description.command")
		val timeoutDescription: String = settings.find("core.tool.bash.property.description.timeout.seconds")
		val envIdsDescription: String = settings.find("core.tool.bash.property.description.env.ids")
		val defaultTimeoutSeconds: Int = settings.find("core.tool.bash.setting.default.timeout.seconds")
		val envIds = listEnv().sorted().joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" }.ifBlank { "<none>" }
		return Tool.Meta(
			name = "bash",
			description = description,
			functions = listOf(
				Tool.Function(
					name = "run",
					description = runDescription,
					parameters = mapOf(
						"command" to Tool.Function.Property(
							description = commandDescription,
							required = true,
							valueType = Tool.Function.Property.ValueType.StringValue(),
						),
						"timeout_seconds" to Tool.Function.Property(
							description = timeoutDescription.format(defaultTimeoutSeconds),
							required = false,
							valueType = Tool.Function.Property.ValueType.IntegerValue(),
						),
						"env_ids" to Tool.Function.Property(
							description = envIdsDescription.format(envIds),
							required = false,
							valueType = Tool.Function.Property.ValueType.ArrayValue(Tool.Function.Property.ValueType.StringValue()),
						),
					),
				),
			),
		)
	}
	
	override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
		val runtime = runtime(input.settings)
		
		val command = input.arguments["command"]!!.jsonPrimitive.content
		if (command.isBlank()) {
			logger.debug("Rejected blank bash command  tool=bash")
			return Tool.ToolOutput(runtime.messageInvalidCommand, false)
		}
		val timeoutSeconds = input.arguments["timeout_seconds"]?.jsonPrimitive?.int ?: runtime.defaultTimeoutSeconds
		if (timeoutSeconds <= 0) {
			logger.debug("Rejected invalid bash timeout  tool=bash  timeout={}", timeoutSeconds)
			return Tool.ToolOutput(runtime.messageInvalidTimeout, false)
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
		
		val output = runtime.messageResultTemplate.format(result.exitCode, duration, stdout, stderr)
		return Tool.ToolOutput(output, result.exitCode == 0 && !result.timeout)
	}
	
	private data class Runtime(
		val defaultTimeoutSeconds: Int,
		val messageInvalidTimeout: String,
		val messageInvalidCommand: String,
		val messageResultTemplate: String,
	)
	
	private fun runtime(settings: List<SettingItem>) = Runtime(
		defaultTimeoutSeconds = settings.find("core.tool.bash.setting.default.timeout.seconds"),
		messageInvalidTimeout = settings.find("core.tool.bash.message.error.invalid.timeout"),
		messageInvalidCommand = settings.find("core.tool.bash.message.error.invalid.command"),
		messageResultTemplate = settings.find("core.tool.bash.message.result.template"),
	)
	
	fun listEnv(): List<String> = envStorage.listEnv()
	
	fun getEnv(id: String): String? = envStorage.getEnv(id)
	
	fun setEnv(id: String, value: String) = envStorage.setEnv(id, value)
	
	fun removeEnv(id: String) = envStorage.removeEnv(id)
}
