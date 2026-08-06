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

package io.github.autotweaker.adapter.cli.debugger.command

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.adapter.cli.debugger.CliDebugger
import io.github.autotweaker.adapter.cli.syntax.XOR
import io.github.autotweaker.adapter.cli.syntax.buildSyntax
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.INDENT
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef

@AutoService(Command::class)
class Debug : Command, I18nable {
	override val name = "debug"
	override val description get() = i18n(Description())
	override val syntax = buildSyntax(XOR) {
		flag("list-db", ParamListDb()) { aliases() }
		all {
			xor {
				value("list", ParamList())
				value("get", ParamGet())
				value("put", ParamPut())
				value("delete", ParamDelete())
			}
			xor {
				flag("setting", Table()) { aliases() }
				flag("jsonStore", Table()) { aliases() }
				flag("sessionData", Table()) { aliases() }
				flag("agentData", Table()) { aliases() }
				flag("sessionMessage", Table()) { aliases() }
				flag("secrets", Table()) { aliases() }
			}
		}
		
	}
	private val debug get() = CliDebugger.instance
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		if (hasArg("list-db")) {
			debug.tables().forEach { (db, table) ->
				out(db)
				table.forEach { (name, count) ->
					out("$INDENT$name: $count")
				}
			}
			done()
		}
		
		with(DebugHandler(debug)) {
			handle()
		}
		done()
	}
	
	@AutoService(I18nDef::class)
	class Description : I18nBase(zh("读写应用数据库"))
	
	@AutoService(I18nDef::class)
	class ParamListDb : I18nBase(zh("列出连接到的数据库和表"))
	
	@AutoService(I18nDef::class)
	class ParamList : I18nBase(zh("列出指定区间所有条目"))
	
	@AutoService(I18nDef::class)
	class ParamGet : I18nBase(zh("获取指定key的条目"))
	
	@AutoService(I18nDef::class)
	class ParamPut : I18nBase(zh("更新指定key的条目"))
	
	@AutoService(I18nDef::class)
	class ParamDelete : I18nBase(zh("删除指定key的条目"))
	
	@AutoService(I18nDef::class)
	class Table : I18nBase(zh("指定此表"))
}
