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

actual fun stdoutIsTty() = isatty(STDOUT_FILENO) == 1

actual fun stdinIsTty() = isatty(STDIN_FILENO) == 1

actual fun beginNoEcho() = Terminal.beginNoEcho()

actual fun endNoEcho() = Terminal.endNoEcho()

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
actual fun readStdinChunk(buffer: ByteArray): Int {
	val n = read(STDIN_FILENO, buffer.refTo(0), buffer.size.toULong())
	if (n < 0 && errno == EINTR) return readStdinChunk(buffer)
	return n.toInt()
}

actual fun readPrompt(echo: Boolean): String? {
	Terminal.flushStdin()
	val fd = Terminal.interactiveFd()
	if (echo) {
		Terminal.setEcho(fd, true)
		try {
			return InputReader.readTtyLine(fd)
		} finally {
			Terminal.setEcho(fd, false)
		}
	}
	return InputReader.readPasswordTty(fd)
}

@OptIn(ExperimentalForeignApi::class)
actual fun env(name: String): String {
	val valuePtr = getenv(name)
	return valuePtr?.toKString() ?: ""
}

@OptIn(ExperimentalForeignApi::class)
actual fun cwd(): String = memScoped {
	val buffer = allocArray<ByteVar>(4096)
	getcwd(buffer, 4096u.toULong())?.toKString() ?: env("HOME")
}

@OptIn(ExperimentalForeignApi::class)
actual fun flushOutput() {
	fflush(stdout)
}

@OptIn(ExperimentalForeignApi::class)
actual fun printErr(content: String) {
	fprintf(stderr, "%s", content)
	fflush(stderr)
}
