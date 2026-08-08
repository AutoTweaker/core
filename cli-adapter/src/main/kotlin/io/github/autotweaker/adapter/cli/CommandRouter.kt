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
import io.github.autotweaker.adapter.cli.commands.Command
import io.github.autotweaker.adapter.cli.commands.DoneException
import io.github.autotweaker.adapter.cli.commands.help.Help
import io.github.autotweaker.adapter.cli.console.CmdOutput
import io.github.autotweaker.adapter.cli.console.ConsoleImpl
import io.github.autotweaker.adapter.cli.console.Request
import io.github.autotweaker.adapter.cli.syntax.ArgParser
import io.github.autotweaker.adapter.cli.syntax.SyntaxValidator
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import java.util.*


class CommandRouter(private val core: CoreAPI, commands: List<Command>) : Loggable, I18nable {
	private val help = Help(commands)
	private val handlers: Map<String, Command> = commands.associateBy { it.name }
	
	@AutoService(SettingDef::class)
	class MaxArgsCount : IntSetting(
		100_000, zh(
			"CLI命令的最大参数数量，超出会报错"
		)
	)
	
	private val maxArgsCount = MaxArgsCount().get()
	private val argParser = ArgParser(maxArgsCount)
	
	init {
		log.debug("Loaded CommandRouter  commandCount={}  commands={}", handlers.size, handlers.keys)
	}
	
	companion object {
		fun fromServiceLoader(core: CoreAPI): CommandRouter = CommandRouter(
			core, ServiceLoader.load(Command::class.java, CliAdapter::class.java.classLoader).toList()
		)
	}
	
	suspend fun dispatch(
		request: CliMessage.Command,
		prompt: suspend (echo: Boolean) -> String,
		output: suspend (CmdOutput) -> Unit
	): Int = try {
		//取子命令
		val cmd = request.command()
		//无参at
		if (cmd == null) {
			output(CmdOutput("$APP_NAME  Copyright (C) 2026  WhiteElephant-abc\n"))
			return 0
		}
		if (cmd == help.name) {
			val console = ConsoleImpl(
				isTty = request.isTty,
				request = Request(emptyMap(), emptyList(), emptyMap()),
				output = output,
				readLine = prompt
			)
			with(help) {
				console.executePath(request.args.drop(1))
			}
		}
		
		//找子命令
		var command = handlers[cmd] ?: run {
			output(
				CmdOutput(
					i18n(CmdI18n.UnknownHint(), cmd, request.prog) + '\n',
					OutputChannel.STDERR
				)
			)
			log.warn("Received unknown command  command={}  args={}", cmd, request.args)
			return 1
		}
		
		var args = request.args.drop(1)
		while (true) {
			command = command.children.find { it.name == args.firstOrNull() } ?: break
			args = args.drop(1)
		}
		
		val conflicts = SyntaxValidator.checkConflicts(command.syntax)
		if (conflicts.isNotEmpty()) {
			conflicts.forEach {
				output(CmdOutput("Error: $it\n", OutputChannel.STDERR))
			}
			log.warn("Detected param name conflict in command  command={}  conflicts={}", command.name, conflicts)
			return 1
		}
		
		log.debug("Dispatched command  command={}  args={}", command.name, args)
		val parsed = argParser.parse(args, command.syntax)
			?: run {
				log.debug("Rejected invalid arguments for command  command={}", command.name)
				output(
					CmdOutput(
						i18n(CmdI18n.InvalidArgs(), command.name, request.prog) + '\n',
						OutputChannel.STDERR
					)
				)
				return 1
			}
		
		if (command.requiresKeystore && !core.secret.isUnlocked.value) {
			log.debug("Rejected command, keystore locked  command={}", command.name)
			
			output(
				CmdOutput(
					i18n(CmdI18n.KeystoreLocked(), request.prog) + '\n',
					OutputChannel.STDERR
				)
			)
			return 1
		}
		
		val console = ConsoleImpl(
			isTty = request.isTty,
			request = parsed,
			output = output,
			readLine = prompt
		)
		
		with(command) {
			console.execute(core)
		}
	} catch (e: DoneException) {
		return e.exitCode
	}
}
