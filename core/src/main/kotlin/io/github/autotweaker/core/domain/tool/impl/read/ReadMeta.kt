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

package io.github.autotweaker.core.domain.tool.impl.read

import io.github.autotweaker.api.Settable
import io.github.autotweaker.api.setting
import io.github.autotweaker.api.tool.buildMeta

@Suppress("DuplicatedCode")
object ReadMeta : Settable {
	val meta = buildMeta("read", setting(ReadSettings.DescriptionSetting())) {
		function("file") {
			description {
				setting(ReadSettings.FileFuncDescriptionSetting()).format(
					setting(ReadSettings.FileMaxCharsSetting()),
					setting(ReadSettings.FileMaxLinesSetting())
				)
			}
			
			string("file_path") {
				description { setting(ReadSettings.FilePathPropDescriptionSetting()) }
			}
			int("start_line") {
				description { setting(ReadSettings.StartLinePropDescriptionSetting()) }
			}
			int("end_line") {
				description { setting(ReadSettings.EndLinePropDescriptionSetting()) }
			}
			boolean("line_number") {
				required = false
				description { setting(ReadSettings.LineNumberPropDescriptionSetting()) }
			}
		}
		
		function("summarize") {
			description {
				setting(ReadSettings.SummarizeFuncDescriptionSetting()).format(
					setting(ReadSettings.SummarizeMaxInputCharsSetting()),
					setting(ReadSettings.SummarizeMinCharsSetting()),
					setting(ReadSettings.SummarizeMaxLinesSetting())
				)
			}
			
			string("file_path") {
				description { setting(ReadSettings.FilePathPropDescriptionSetting()) }
			}
			int("start_line") {
				description { setting(ReadSettings.StartLinePropDescriptionSetting()) }
			}
			int("end_line") {
				description { setting(ReadSettings.EndLinePropDescriptionSetting()) }
			}
			string("prompt") {
				required = false
				description { setting(ReadSettings.SummarizePromptPropDescriptionSetting()) }
			}
		}
		
		function("unicode") {
			description { setting(ReadSettings.UnicodeFuncDescriptionSetting()) }
			
			string("file_path") {
				description { setting(ReadSettings.FilePathPropDescriptionSetting()) }
			}
			int("start_char") {
				required = false
				description { setting(ReadSettings.UnicodeStartCharPropDescriptionSetting()) }
			}
			int("max_chars") {
				description {
					setting(ReadSettings.UnicodeMaxCharsPropDescriptionSetting())
						.format(setting(ReadSettings.UnicodeMaxCharsSetting()))
				}
			}
		}
	}
}
