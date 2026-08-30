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

package io.github.autotweaker.core.domain.agent.chat

import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrDefault
import io.github.autotweaker.api.types.agent.ContextInjection
import io.github.autotweaker.api.types.agent.MessageContent
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.core.domain.port.GitStatusService
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.SystemInfoService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import java.util.*
import kotlin.time.Instant

class MessageConverts(
	private val fileSystem: RawFileSystem,
	private val pathResolver: PathResolver,
	private val systemInfo: SystemInfoService,
	private val gitService: GitStatusService
) : Traceable, I18nable {
	suspend fun environmentInjection(workspace: Path) = buildList {
		val inContainer = pathResolver.inContainer(workspace)
		val cwd = if (inContainer) pathResolver.toContainerPath(workspace) else workspace
		trace.catching {
			fileSystem.read(CONFIG_PATH.resolve("AGENTS.md"))
		}.getOrNull()?.let {
			add(buildInjection("user_instructions", it.content))
		}
		trace.catching {
			fileSystem.read(workspace.resolve("AGENTS.md"))
		}.getOrNull()?.let {
			add(buildInjection("project_instructions", it.content))
		}
		if (!inContainer) trace.catching {
			systemInfo.get()
		}.getOrNull()?.let {
			add(
				buildInjection(
					"system_environment",
					InjectionSettings.SystemEnvironment().format(
						it.osName,
						it.hostname,
						it.user,
						it.distribution,
						it.kernelVersion,
						it.cpuArch,
						it.cpuCoreCount,
						it.totalMemory
					)
				)
			)
		}
		val isRepository = trace.catching {
			gitService.isRepository(workspace)
		}.getOrDefault(false)
		add(
			buildInjection(
				"workspace_environment",
				InjectionSettings.WorkspaceEnvironment().format(
					cwd,
					inContainer,
					isRepository,
					trace.catching {
						buildString {
							val files = fileSystem.list(workspace).take(1000)
							if (files.isEmpty()) appendLine("[empty]")
							files.forEach {
								val path = if (inContainer) pathResolver.toContainerPath(it) else it
								appendLine(path)
							}
						}
					}.getOrNull()
				)
			)
		)
		trace.catching {
			if (isRepository) add(
				buildInjection(
					"git_info", InjectionSettings.GitEnvironment().format(
						gitService.head(workspace),
						gitService.branch(workspace),
						gitService.remote(workspace),
						gitService.log(workspace, InjectionSettings.GitLogCount().get())
							.joinToString("\n")
					)
				)
			)
		}
	}
	
	private fun buildInjection(
		tag: String,
		content: String
	) = ContextInjection(
		id = uuidOf(tag),
		tag = tag,
		content = content
	)
	
	private fun uuidOf(tag: String) =
		UUID.nameUUIDFromBytes("ENVIRONMENT_INJECTION-$tag".toByteArray())
}

fun MessageContent.inject() = content.inject(injections)

fun List<ContentPart>?.inject(
	injections: List<ContextInjection>?
): List<ContentPart> = buildList {
	injections?.forEach { add(ContentPart.Text(it.toXml())) }
	this@inject?.let { addAll(it) }
}

fun MessageContent.injectContext(
	timestamp: Instant,
	timeZone: TimeZone,
	language: Locale
) = copy(
	injections = listOf(
		ContextInjection(
			"utc_time", timestamp
		), ContextInjection(
			"local_time", timestamp.toLocalDateTime(timeZone)
		), ContextInjection(
			"timezone", timeZone
		), ContextInjection(
			"language", language
		)
	) + injections.orEmpty()
)

fun List<ChatMessage>.inject(
	injections: List<ContextInjection>?, summarize: String?
): List<ChatMessage> = injectAtFirst(buildList {
	summarize?.let {
		add(
			ContextInjection(
				"summary",
				summarize
			)
		)
	}
	injections?.let {
		addAll(it)
	}
})

fun List<ContentPart>.merge(): String = buildString {
	this@merge.forEach {
		if (it is ContentPart.Text) appendLine(it.content)
		else appendLine("<media />")
	}
}

fun List<ChatMessage>.injectAtFirst(injections: List<ContextInjection>?): List<ChatMessage> {
	if (injections.isNullOrEmpty()) return this
	val firstUserIndex = indexOfFirst { it is ChatMessage.User }
	if (firstUserIndex == -1) return this
	val mutable = toMutableList()
	val userMsg = mutable[firstUserIndex] as ChatMessage.User
	mutable[firstUserIndex] = userMsg.copy(content = userMsg.content.inject(injections))
	return mutable
}

fun ContextInjection.toXml() =
	if (content.lines().count() <= 1) "<$tag>$content</$tag>"
	else "<$tag>\n$content\n</$tag>"
