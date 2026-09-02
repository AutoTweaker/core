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
	val unescapeConfig = buildDeclaration {
		enum("unescape_config", "disable", "default", "lenient_mode")
	}
	
	val replacement = buildDeclaration {
		obj("replacement") {
			int("line_from") {
				required = false
			}
			int("line_to") {
				required = false
			}
			string("old_string")
			param("unescape_old", unescapeConfig) {
				required = false
			}
			string("new_string")
			param("unescape_new", unescapeConfig) {
				required = false
			}
		}
	}
	
	val edits = buildDeclaration {
		list(replacement)
	}
	
	function("file") {
		string("file_path")
		string("sha256")
		param("edits", edits)
	}
}.gen(
	"io.github.autotweaker.api.generated.tool.args",
	"io.github.autotweaker.core.domain.tool.impl.edit",
)
