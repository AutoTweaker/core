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
import io.github.autotweaker.adapter.cli.console.Ansi
import io.github.autotweaker.adapter.cli.console.CmdOutput
import io.github.autotweaker.adapter.cli.console.ConsoleImpl
import io.github.autotweaker.adapter.cli.console.Request
import io.github.autotweaker.adapter.cli.syntax.ArgParser
import io.github.autotweaker.adapter.cli.syntax.SyntaxValidator
import io.github.autotweaker.api.*
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrDefault
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import kotlinx.coroutines.channels.ReceiveChannel
import java.nio.file.Path
import java.util.*


class CommandRouter(private val core: CoreAPI, commands: List<Command>) :
	Loggable, I18nable, Traceable {
	constructor(core: CoreAPI) : this(
		core, ServiceLoader.load(
			Command::class.java,
			CliAdapter::class.java.classLoader
		).toList()
	)
	
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
		log.info("Loaded CommandRouter  commandCount={}  commands={}", handlers.size, handlers.keys)
	}
	
	suspend fun dispatch(
		request: CliMessage.Command,
		requestId: String,
		stdin: ReceiveChannel<String>,
		prompt: suspend (echo: Boolean) -> String?,
		output: suspend (CmdOutput) -> Unit
	): Int = try {
		val cwd = trace.catching { Path.of(request.cwd) }
			.onFailure { log.error("Failed to converting cwd", it) }
			.getOrDefault(HOME)
		
		suspend fun String.error() = output(
			CmdOutput(
				"${
					if (request.isTty) Ansi.styled(this, Ansi.RED)
					else this
				}\n",
				OutputChannel.STDERR
			)
		)
		
		//取子命令
		val cmd = request.command()
		//无参at
		if (cmd == null) {
			output(CmdOutput("$APP_NAME  Copyright (C) 2026  WhiteElephant-abc\n"))
			return 0
		}
		if (cmd == help.name) {
			val console = ConsoleImpl(
				cwd = cwd,
				isTty = request.isTty,
				request = Request(emptyMap(), emptyList(), emptyMap()),
				stdin = stdin,
				output = output,
				readInput = prompt
			)
			with(help) {
				console.executePath(request.args.drop(1))
			}
		}
		
		//找子命令
		var command = handlers[cmd] ?: run {
			i18n(CmdI18n.UnknownHint(), cmd, request.prog).error()
			log.warn("Received unknown command  command={}  requestId={}  args={}", cmd, requestId, request.args)
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
				"Error: $it".error()
			}
			log.warn(
				"Detected param name conflict in command  command={}  requestId={}  conflicts={}",
				command.name,
				requestId,
				conflicts
			)
			return 1
		}
		
		log.debug("Dispatched command  command={} requestId={}  args={}", command.name, requestId, args)
		val parsed = argParser.parse(args, command.syntax)
			?: run {
				log.debug("Rejected invalid arguments for command  command={} requestId={}", requestId, command.name)
				i18n(CmdI18n.InvalidArgs(), request.command(), request.prog).error()
				return 1
			}
		
		if (command.requiresKeystore && !core.secret.isUnlocked.value) {
			log.debug("Rejected command, keystore locked  command={} requestId={}", requestId, command.name)
			
			i18n(CmdI18n.KeystoreLocked(), request.prog).error()
			return 1
		}
		
		val console = ConsoleImpl(
			cwd = cwd,
			isTty = request.isTty,
			request = parsed,
			stdin = stdin,
			output = output,
			readInput = prompt
		)
		
		with(command) {
			console.execute(core)
		}
	} catch (e: DoneException) {
		return e.exitCode
	}
}
