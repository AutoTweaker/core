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

package io.github.autotweaker.adapter.cli.commands.workspace

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrElse
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.trace
import io.github.autotweaker.api.types.exception.notfound.WorkspaceNotFoundException
import io.github.autotweaker.api.types.session.WorkspaceMeta

@AutoService(Command::class)
class Workspace : Command, Traceable {
	override val name = "workspace"
	override val description = i18n(WorkspaceI18n.Desc())
	override val syntax = buildSyntax(XOR) {
		flag("list", WorkspaceI18n.List())
		all {
			value("create", WorkspaceI18n.Create())
			value("directory", WorkspaceI18n.Directory()) {
				required = false
				aliases("d", "dir")
			}
		}
		all {
			value("rename", WorkspaceI18n.Rename()) { aliases() }
			positional("new-name", WorkspaceI18n.NewName())
		}
		all {
			value("delete", WorkspaceI18n.Delete()) { aliases("rm") }
			flag("yes", WorkspaceI18n.SkipConfirm()) { required = false }
		}
	}
	override val requiresKeystore = false
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("list") {
			core.workspace.list().forEach {
				out(WorkspaceI18n.ListFormat(), it.meta.displayName, it.meta.path, it.sessionIds.count())
			}
		}
		handleValue("create") { displayName ->
			val path = trace.catching { getValueOrNull("directory")?.let { cwd.resolve(it) } }
				.getOrElse { error(WorkspaceI18n.InvalidPath()) } ?: cwd
			val data = core.workspace.create(
				WorkspaceMeta(
					displayName = displayName,
					path = path
				)
			)
			out(WorkspaceI18n.Name(), data.meta.displayName)
			out(WorkspaceI18n.Path(), data.meta.path)
		}
		handleValue("rename") { displayName ->
			var data = findWorkspace(core, displayName)
			core.workspace.rename(data.meta.id, getPositional(0))
			data = core.workspace.get(data.meta.id) ?: throw WorkspaceNotFoundException(data.meta.id)
			out(WorkspaceI18n.Name(), data.meta.displayName)
			out(WorkspaceI18n.Path(), data.meta.path)
		}
		handleValue("delete") { displayName ->
			val data = findWorkspace(core, displayName)
			if (!hasArg("yes") && !confirm(WorkspaceI18n.Confirm(), displayName, data.meta.path))
				done(1)
			core.workspace.delete(data.meta.id)
		}
		done(1)
	}
	
	private suspend fun Console.findWorkspace(core: CoreAPI, displayName: String) =
		core.workspace.list().find { it.meta.displayName == displayName }
			?: error(WorkspaceI18n.NotFound(), displayName)
}
