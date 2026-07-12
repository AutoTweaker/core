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

package io.github.autotweaker.core.domain.tool.impl.edit

import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.tool.buildDeclaration
import io.github.autotweaker.api.tool.buildMeta

object EditMeta : Settable {
	val meta = buildMeta("edit", setting(EditPrompt.EditDesc())) {
		function("run") {
			stringList("files")
			param("actions", editActionList)
			boolean("dry_run") { required = false }
		}
		function("apply") { string("operation_id") }
		function("get_clip") { string("clip_id") }
	}
	
	private val editActionList = buildDeclaration { list(editAction) }
	
	private val editAction = buildDeclaration {
		oneOf("edit_action") {
			variant("insert") {
				param("at", charLocator)
				string("content")
			}
			variant("replace") {
				param("at", selection)
				string("content")
			}
			variant("delete") { param("at", selection) }
			variant("copy") { param("at", selection) }
			variant("cut") { param("at", selection) }
			variant("paste") {
				param("at", charLocator)
				string("clip_id")
			}
		}
	}
	
	private val selection = buildDeclaration {
		oneOf("selection") {
			variant("line") { param("at", lineLocator) }
			variant("lines") {
				param("start", lineLocator)
				param("end", lineLocator)
			}
			variant("range") {
				param("start", charLocator)
				param("end", charLocator)
			}
			variant("multi") {
				string("match_all")
				param("in_range", lineRange) { required = false }
			}
		}
	}
	
	private val charLocator = buildDeclaration {
		oneOf("char_locator") {
			variant("by_char") {
				param("line", lineLocator)
				int("char")
			}
			variant("by_match") {
				param("line", lineLocator)
				string("match")
				int("occurrence") { required = false }
				param("side", side) { required = false }
			}
			variant("by_match_all") {
				param("line", lineLocator)
				string("match_all")
				param("side", side) { required = false }
			}
		}
	}
	
	private val lineLocator = buildDeclaration {
		oneOf("line_locator") {
			variant("by_number") { int("line") }
			variant("by_match") {
				string("match")
				param("in_range", lineRange) { required = false }
				int("occurrence") { required = false }
			}
			variant("by_match_all") {
				string("match_all")
				param("in_range", lineRange) { required = false }
			}
			variant("by_edge") { param("edge", lineEdge) }
		}
	}
	
	private val lineRange = buildDeclaration {
		obj("line_range") {
			param("from", linePosition)
			param("to", linePosition)
		}
	}
	
	private val linePosition = buildDeclaration {
		oneOf("line_position") {
			variant("value") { int("n") }
			variant("pos") { param("position", lineEdge) }
		}
	}
	
	private val lineEdge = buildDeclaration { enum("line_edge", "start", "end") }
	private val side = buildDeclaration { enum("side", "before", "after") }
}
