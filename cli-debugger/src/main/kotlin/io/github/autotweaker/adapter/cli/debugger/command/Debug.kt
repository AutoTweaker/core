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
import io.github.autotweaker.adapter.cli.*
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.debugger.CliDebugger
import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.i18n
import io.github.autotweaker.api.i18n.I18nDef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@AutoService(Command::class)
class Debug : Command, I18nable {
	override val name = "debug"
	override val description get() = i18n(Description())
	override val syntax
		get() = Syntax.xor(
			Syntax.leaf(Param.Type.FLAG, "list-db", ParamListDb(), aliases = emptyList()),
			Syntax.all(
				Syntax.xor(
					Syntax.leaf(Param.Type.VALUE, "list", ParamList()),
					Syntax.leaf(Param.Type.VALUE, "get", ParamGet()),
					Syntax.leaf(Param.Type.VALUE, "put", ParamPut()),
					Syntax.leaf(Param.Type.VALUE, "delete", ParamDelete()),
				),
				Syntax.xor(
					Syntax.leaf(Param.Type.FLAG, "setting", Table(), aliases = emptyList()),
					Syntax.leaf(Param.Type.FLAG, "jsonStore", Table(), aliases = emptyList()),
					Syntax.leaf(Param.Type.FLAG, "sessionData", Table(), aliases = emptyList()),
					Syntax.leaf(Param.Type.FLAG, "agentData", Table(), aliases = emptyList()),
					Syntax.leaf(Param.Type.FLAG, "sessionMessage", Table(), aliases = emptyList()),
					Syntax.leaf(Param.Type.FLAG, "secrets", Table(), aliases = emptyList()),
				),
			),
		)
	private lateinit var core: CoreAPI
	private val debug get() = CliDebugger.instance
	
	override fun init(core: CoreAPI) {
		this.core = core
	}
	
	override fun handle(
		request: Request, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> = flow {
		if (request.has("list-db")) {
			debug.tables().forEach { (db, table) ->
				emit(CmdOutput.Data(db))
				table.forEach { (name, count) ->
					emit(CmdOutput.Data("$SPACE$name: $count"))
				}
			}
			emitDone()
			return@flow
		}
		
		emitAll(DebugHandler(debug, prompt).handle(request))
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
	
	companion object {
		const val SPACE = "    "
	}
}
