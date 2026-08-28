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

package io.github.autotweaker.adapter.cli.commands.session.usage

import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.commands.model.Model.Companion.findModel
import io.github.autotweaker.adapter.cli.commands.model.ModelI18n
import io.github.autotweaker.adapter.cli.syntax.ALL
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.i18n

class SessionUsage : Command {
	override val name = "usage"
	override val description = i18n(SessionUsageI18n.Desc())
	override val syntax = buildSyntax(ALL) {
		required = false
		
		positional("provider", ModelI18n.ParamProvider())
		positional("model", ModelI18n.ParamName())
	}
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		val usage = if (getPositional().size == 2)
			core.persistence.mergeUsage(findModel(core), null, null)
		else
			core.persistence.mergeUsage(null, null, null)
		if (usage == null) error(SessionUsageI18n.NoUsage())
		
		out(SessionUsageI18n.TotalTokens(), usage.totalTokens)
		out(SessionUsageI18n.PromptTokens(), usage.promptTokens)
		out(SessionUsageI18n.CompletionTokens(), usage.completionTokens)
		out(SessionUsageI18n.ReasoningTokens(), usage.reasoningTokens)
		out(SessionUsageI18n.CacheHitRate(), usage.cacheHitRateStr)
		done()
	}
}
