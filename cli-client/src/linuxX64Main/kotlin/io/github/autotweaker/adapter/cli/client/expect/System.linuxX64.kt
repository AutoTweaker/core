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

package io.github.autotweaker.adapter.cli.client.expect

import io.github.autotweaker.adapter.cli.client.CommandResult
import kotlinx.cinterop.*
import platform.posix.*


@OptIn(ExperimentalForeignApi::class)
actual fun exec(vararg args: String): CommandResult {
	val stdoutBuilder = StringBuilder()
	
	memScoped {
		val pipeFds = allocArray<IntVar>(2)
		if (pipe(pipeFds) == -1) return CommandResult(1, "")
		
		val readFd = pipeFds[0]
		val writeFd = pipeFds[1]
		
		val pid = fork()
		if (pid == -1) {
			close(readFd); close(writeFd)
			return CommandResult(1, "")
		}
		
		if (pid == 0) {
			close(readFd)
			
			dup2(writeFd, STDOUT_FILENO)
			dup2(writeFd, STDERR_FILENO)
			close(writeFd)
			
			val cArgs = allocArray<CPointerVar<ByteVar>>(args.size + 1)
			args.forEachIndexed { index, arg ->
				cArgs[index] = arg.cstr.getPointer(this)
			}
			cArgs[args.size] = null
			
			execvp(args[0], cArgs)
			_exit(127)
		}
		
		close(writeFd)
		
		val fp = fdopen(readFd, "r")
		if (fp != null) {
			val buffer = allocArray<ByteVar>(1024)
			while (fgets(buffer, 1024, fp) != null) {
				stdoutBuilder.append(buffer.toKString())
			}
			fclose(fp)
		} else {
			close(readFd)
		}
		
		val statusVar = alloc<IntVar>()
		waitpid(pid, statusVar.ptr, 0)
		val rawStatus = statusVar.value
		val exitCode = (rawStatus shr 8) and 0xFF
		
		return CommandResult(exitCode, stdoutBuilder.toString())
	}
}

@OptIn(ExperimentalForeignApi::class)
actual fun promptOrStdin(prompt: String, echo: Boolean): String {
	print(prompt)
	fflush(null)
	
	if (echo) {
		val input = readlnOrNull() ?: ""
		if (isatty(STDIN_FILENO) != 1) println(input)
		return input
	}
	
	return memScoped {
		val tty = alloc<termios>()
		val oldTty = alloc<termios>()
		val stdinFd = STDIN_FILENO
		
		val isAtty = isatty(stdinFd) == 1
		
		if (isAtty) {
			tcgetattr(stdinFd, oldTty.ptr)
			tcgetattr(stdinFd, tty.ptr)
			
			tty.c_lflag = tty.c_lflag and ECHO.toUInt().inv()
			tty.c_lflag = tty.c_lflag and ICANON.toUInt().inv()
			
			tcsetattr(stdinFd, TCSANOW, tty.ptr)
		}
		
		val inputBuilder = StringBuilder()
		
		try {
			while (true) {
				val ch = getchar()
				
				if (ch == -1) break
				
				when (ch) {
					10, 13 -> break
					
					
					127, 8 -> if (inputBuilder.isNotEmpty()) {
						inputBuilder.deleteAt(inputBuilder.length - 1)
						
						print("\b \b")
						fflush(null)
					}
					
					
					else -> if (ch in 32..126) {
						inputBuilder.append(ch.toChar())
						
						print("*")
						fflush(null)
					}
					
				}
			}
			inputBuilder.toString()
		} finally {
			if (isAtty) {
				tcsetattr(stdinFd, TCSANOW, oldTty.ptr)
			}
			println()
			fflush(null)
		}
	}
}

@OptIn(ExperimentalForeignApi::class)
actual fun env(name: String): String {
	val valuePtr = getenv(name)
	return valuePtr?.toKString() ?: ""
}

@OptIn(ExperimentalForeignApi::class)
actual fun printErr(content: String) {
	fprintf(stderr, "%s", content)
	fflush(stderr)
}
