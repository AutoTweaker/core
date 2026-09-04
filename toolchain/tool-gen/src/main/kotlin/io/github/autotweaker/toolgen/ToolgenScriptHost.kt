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

package io.github.autotweaker.toolgen

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.system.exitProcess

fun main(args: Array<String>) {
	if (args.size != 2) {
		System.err.println("usage: <outputDir> <scriptsDir>")
		exitProcess(2)
	}
	val outputDir = File(args[0]).apply { mkdirs() }
	val scriptsDir = File(args[1])
	if (!scriptsDir.isDirectory) {
		System.err.println("scripts dir not found: $scriptsDir")
		exitProcess(2)
	}
	val scripts = scriptsDir.listFiles { it.isFile && it.name.endsWith(".toolgen.kts") }
		?.sortedBy { it.name }
		?: emptyList()
	if (scripts.isEmpty()) {
		System.err.println("no *.toolgen.kts under $scriptsDir")
		exitProcess(2)
	}
	val host = BasicJvmScriptingHost()
	var failed = false
	for (script in scripts) {
		System.setProperty("toolgen.outputDir", outputDir.absolutePath)
		print("executing ${script.name}... ")
		val result = runBlocking {
			host.eval(
				script.toScriptSource(),
				createJvmCompilationConfigurationFromTemplate<ToolgenScript>(),
				ScriptEvaluationConfiguration()
			)
		}
		if (result is ResultWithDiagnostics.Success) {
			println("ok")
		} else {
			failed = true
			println("failed")
			result.reports.forEach { report ->
				System.err.println(report.message + (report.exception?.let { ": $it" } ?: ""))
			}
		}
	}
	if (failed) exitProcess(1)
}
