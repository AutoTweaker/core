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

package io.github.autotweaker.adapter.cli

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitDone
import io.github.autotweaker.adapter.cli.CmdOutput.Companion.emitI18n
import io.github.autotweaker.adapter.cli.commands.help.Help
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.*


class CommandRouter(private val core: CoreAPI, commands: List<Command>) : Loggable, Settable {
	private val handlers: Map<String, Command>
	
	@AutoService(SettingDef::class)
	class MaxArgsCount : IntSetting(
		100_000, zh(
			"CLI命令的最大参数数量，超出会报错"
		)
	)
	
	private val maxArgsCount = setting(MaxArgsCount())
	private val argParser = ArgParser(maxArgsCount)
	
	init {
		commands.forEach { it.init(core) }
		val help = Help(commands)
		handlers = (commands + help).associateBy { it.name }
		log.debug("Loaded CommandRouter  commandCount={}  commands={}", handlers.size, handlers.keys)
	}
	
	companion object {
		fun fromServiceLoader(core: CoreAPI): CommandRouter = CommandRouter(
			core, ServiceLoader.load(Command::class.java, CliAdapter::class.java.classLoader).toList()
		)
	}
	
	fun dispatch(
		request: CliMessage.Command, prompt: suspend (text: String, echo: Boolean) -> String
	): Flow<CmdOutput> {
		//取子命令
		val cmd = request.command()
		//无参at
		if (cmd.isEmpty()) {
			return flowOf(
				CmdOutput.Data("$APP_NAME  Copyright (C) 2026  WhiteElephant-abc"), CmdOutput.Done()
			)
		}
		//找子命令
		val handler = handlers[cmd] ?: run {
			log.warn("Received unknown command  command={}  args={}", cmd, request.args)
			return flow {
				emitI18n(CmdI18n.UnknownHint(), cmd, request.prog, error = true)
				emitDone(1)
			}
		}
		
		val conflicts = SyntaxValidator.checkConflicts(handler.syntax)
		if (conflicts.isNotEmpty()) {
			log.warn("Detected param name conflict in command  command={}  conflicts={}", cmd, conflicts)
			return flowOf(
				*conflicts.map { CmdOutput.Data(it, OutputChannel.STDERR) }.toTypedArray(),
				CmdOutput.Done(1),
			)
		}
		
		log.debug("Dispatched command  command={}  args={}", cmd, request.args.drop(1))
		val parsed = argParser.parse(request.args.drop(1), handler.syntax, request.prog)
			?: run {
				log.debug("Rejected invalid arguments for command  command={}", cmd)
				return flow {
					emitI18n(CmdI18n.InvalidArgs(), cmd, request.prog, error = true)
					emitDone(1)
				}
			}
		
		val isSecretUnlock = cmd == "secret" && (parsed.has("unlock") || parsed.has("passwd"))
		if (cmd != "help" && cmd != "version" && !isSecretUnlock && !core.secret.isUnlocked.value) {
			log.debug("Rejected command, keystore locked  command={}", cmd)
			return flow {
				emitI18n(CmdI18n.KeystoreLocked(), request.prog, error = true)
				emitDone(1)
			}
		}
		
		return handler.handle(parsed, prompt)
	}
}
