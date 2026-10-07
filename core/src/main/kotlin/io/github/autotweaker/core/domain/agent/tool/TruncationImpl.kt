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

package io.github.autotweaker.core.domain.agent.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.format
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.core.domain.agent.chat.merge
import io.github.autotweaker.core.domain.port.TemporaryStorage
import io.github.autotweaker.core.domain.tool.port.TruncationService
import java.nio.file.Path

class TruncationImpl(
	private val workspace: () -> Path,
	private val pathResolver: PathResolver,
	private val temporaryStorage: TemporaryStorage
) : TruncationService {
	override fun truncate(content: List<ContentPart>, threshold: Int, keepTail: Boolean): List<ContentPart> {
		var length = 0
		content.forEach {
			if (it is ContentPart.Text) length += it.content.length
		}
		
		if (length <= threshold) return content
		
		content.singleOrNull()?.let {
			if (it !is ContentPart.Text) return@let
			val content = it.content
			return truncate(content, threshold, keepTail).toContentPart()
		}
		
		val truncated = truncate(content.merge(), threshold, keepTail)
		return buildList {
			add(ContentPart.Text(truncated))
			content.forEach {
				if (it !is ContentPart.Text) add(it)
			}
		}
	}
	
	override fun truncate(content: String, threshold: Int, keepTail: Boolean): String {
		if (content.length <= threshold) return content
		val inContainer = pathResolver.inContainer(workspace())
		val (_, hostPath) = temporaryStorage.save(content, inContainer)
		val filePath = if (inContainer) pathResolver.toContainerPath(hostPath) else hostPath
		val prompt = TruncatedPrompt().format(content.length, filePath)
		return if (keepTail) prompt + content.takeLast(threshold) else content.take(threshold) + prompt
	}
	
	@AutoService(SettingDef::class)
	class TruncatedPrompt : StringSetting(
		"[===输出过长（%s 字符），完整内容保存至 `%s`，可以总结、分段读取，或在其中搜索===]", zh(
			"工具输出被截断并保存时的提示"
		)
	)
}
