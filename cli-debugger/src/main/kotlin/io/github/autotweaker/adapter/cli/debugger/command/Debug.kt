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
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.transaction
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef

@AutoService(Command::class)
class Debug : Command {
	override val name = "debug"
	override val description get() = i18n(Description())
	override val syntax = buildSyntax(XOR) {
		flag("list", ParamList()) { aliases() }
		all {
			value("exec", ParamExec())
			positional("sql", ParamSql())
		}
	}
	private val debug get() = CliDebugger.instance
	
	override suspend fun Console.execute(core: CoreAPI): Nothing {
		handleFlag("list") {
			debug.list().forEach { out(it) }
		}
		
		handleValue("exec") { db ->
			val sql = getPositional(0)
			debug.open(db).transaction {
				val lines = exec(sql) { rs ->
					val meta = rs.metaData
					buildList {
						add((1..meta.columnCount).joinToString(" | ") { meta.getColumnLabel(it) })
						while (rs.next()) {
							add((1..meta.columnCount).joinToString(" | ") { rs.getString(it) ?: "NULL" })
						}
					}
				}
				if (lines == null) out("OK")
				else lines.forEach { out(it) }
			}
		}
		
		done(1)
	}
	
	@AutoService(I18nDef::class)
	class Description : I18nBase(zh("读写应用数据库"))
	
	@AutoService(I18nDef::class)
	class ParamList : I18nBase(zh("列出所有数据库"))
	
	@AutoService(I18nDef::class)
	class ParamExec : I18nBase(zh("在指定数据库执行SQL语句"))
	
	@AutoService(I18nDef::class)
	class ParamSql : I18nBase(zh("要执行的SQL语句"))
}
