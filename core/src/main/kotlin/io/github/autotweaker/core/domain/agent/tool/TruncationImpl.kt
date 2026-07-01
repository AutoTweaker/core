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
import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.port.TemporaryStorage
import io.github.autotweaker.core.domain.tool.port.TruncationService

class TruncationImpl(
	private val pathResolver: PathResolver,
	private val workspace: WorkspaceMeta,
	private val temporaryStorage: TemporaryStorage,
) : TruncationService, Settable {
	override fun invoke(content: String, threshold: Int, keepTail: Boolean): String {
		if (content.length <= threshold) return content
		val inContainer = pathResolver.inContainer(workspace.path)
		val (_, hostPath) = temporaryStorage.save(content, inContainer)
		val filePath = if (inContainer) pathResolver.toContainerPath(hostPath) else hostPath
		val prompt = setting.get(TruncatedPrompt()).value.format(content.length, filePath)
		return if (keepTail) prompt + content.takeLast(threshold) else content.take(threshold) + prompt
	}
	
	@AutoService(SettingDef::class)
	class TruncatedPrompt : StringSetting(
		"[===输出过长（%s 字符），完整内容保存至 `%s`，可以总结、分段读取，或在其中搜索===]",
		"工具输出被截断并保存时的提示"
	)
}
