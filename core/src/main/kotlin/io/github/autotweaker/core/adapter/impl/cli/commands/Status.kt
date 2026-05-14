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

package io.github.autotweaker.core.adapter.impl.cli.commands

import com.google.auto.service.AutoService
import io.github.autotweaker.core.adapter.api.CoreAPI
import io.github.autotweaker.core.adapter.api.data.SemVer
import io.github.autotweaker.core.adapter.impl.cli.Command
import io.github.autotweaker.core.adapter.impl.cli.Command.Chunk
import io.github.autotweaker.core.adapter.impl.cli.Request
import io.github.autotweaker.core.adapter.impl.cli.Syntax
import io.github.autotweaker.core.adapter.impl.cli.i18n.I18n
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Status : Command {
	override val name = "status"
	override val description get() = I18n.get("cmd.status.desc")
	override val syntax = Syntax.none()
	private lateinit var core: CoreAPI
	
	override fun init(core: CoreAPI, coreVersion: SemVer) {
		this.core = core
	}
	
	override fun handle(request: Request, prompt: suspend (text: String, echo: Boolean) -> String): Flow<Chunk> = flow {
		val keystoreState = when {
			core.isPasswordEmpty -> I18n.get("status.unlocked")
			core.isUnlocked -> I18n.get("status.unlocked.password_set")
			else -> I18n.get("status.locked")
		}
		emit(Chunk.Data(keystoreState))
		
		val adapters = core.listAdapter()
		emit(Chunk.Data(""))
		emit(Chunk.Data(I18n.get("status.adapters", adapters.size)))
		for (a in adapters) {
			emit(Chunk.Data("  ${a.name}  ${a.version}  ${a.description}"))
		}
		
		val providers = core.config.listProviders()
		emit(Chunk.Data(""))
		emit(Chunk.Data(I18n.get("status.providers", providers.size)))
		for (p in providers) {
			emit(Chunk.Data("  ${p.name}  ${p.type}"))
		}
		
		val models = core.config.listModelIds()
		emit(Chunk.Data(""))
		emit(Chunk.Data(I18n.get("status.models", models.size)))
		for (m in models) {
			emit(Chunk.Data("  $m"))
		}
		
		val sessions = core.session.list()
		emit(Chunk.Data(""))
		emit(Chunk.Data(I18n.get("status.sessions", sessions.size)))
		for (s in sessions) {
			val d = s.data.value
			emit(Chunk.Data("  ${s.id}  ${s.status.value}  ${d.title}"))
		}
		
		val workspaces = core.session.listWorkspaces()
		emit(Chunk.Data(""))
		emit(Chunk.Data(I18n.get("status.workspaces", workspaces.size)))
		for (w in workspaces) {
			emit(Chunk.Data("  ${w.name}  ${w.path}"))
		}
		emit(Chunk.Done())
	}
}