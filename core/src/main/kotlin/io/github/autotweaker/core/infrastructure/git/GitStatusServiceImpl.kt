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

package io.github.autotweaker.core.infrastructure.git

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.core.domain.port.GitStatusService
import java.nio.file.Path

object GitStatusServiceImpl : GitStatusService, Loggable, Traceable {
	override fun isRepository(workspace: Path): Boolean =
		runGit(workspace, "rev-parse", "--git-dir") != null
	
	override fun head(workspace: Path): String? =
		runGit(workspace, "rev-parse", "HEAD")
	
	override fun branch(workspace: Path): String? =
		runGit(workspace, "branch", "--show-current")?.orNull()
	
	override fun remote(workspace: Path): String? =
		runGit(workspace, "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
	
	override fun staged(workspace: Path): List<String> =
		porcelain(workspace) { index, _ -> index != ' ' && index != '?' }
	
	override fun worktree(workspace: Path): List<String> =
		porcelain(workspace) { index, worktree -> worktree != ' ' || index == '?' }
	
	override fun log(workspace: Path, count: Int): List<String> =
		if (count <= 0) emptyList()
		else runGit(workspace, "log", "--oneline", "--decorate", "-n", count.toString())?.lines().orEmpty()
	
	private fun porcelain(workspace: Path, filter: (Char, Char) -> Boolean): List<String> =
		runGit(workspace, "status", "--porcelain")?.lineSequence()
			?.filter { it.length >= 3 }
			?.filter { filter(it[0], it[1]) }
			?.map { it.substring(3) }
			?.toList().orEmpty()
	
	private fun runGit(workspace: Path, vararg args: String): String? = trace.catching {
		val process = ProcessBuilder("git", *args)
			.directory(workspace.toFile())
			.redirectErrorStream(true)
			.start()
		val output = process.inputStream.bufferedReader().readText()
		if (process.waitFor() == 0) output.trim() else null
	}.onFailure {
		log.warn(
			"Failed to run git  args={}  workspace={}  reason={}",
			args.joinToString(" "),
			workspace,
			it.message
		)
	}.getOrNull()
}
