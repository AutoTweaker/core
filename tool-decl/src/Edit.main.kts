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

@file:DependsOn("io.github.autotweaker:tool-gen:0.1.0-alpha.35")

import io.github.autotweaker.toolgen.gen
import io.github.autotweaker.toolgen.tool

tool("edit") {
	function("run") {
		string("file_path")
		int("line_from")
		int("line_to")
		stringList("old_string")
		boolean("json_string")
		string("new_string")
		
	}
	
	
	function("batch") {
		stringList("files")
		string("regex")
		string("replace_with")
	}
	
	function("apply") {
		string("operation_id")
	}
	
}.gen(
	"io.github.autotweaker.api.generated.tool.args",
	"io.github.autotweaker.core.domain.tool.impl.edit",
)
