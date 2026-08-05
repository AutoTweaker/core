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


private var stdinExhausted = false
private var ttyFd: Int = -1

private fun ensureTty(): Int {
	if (ttyFd < 0) {
		ttyFd = open("/dev/tty", O_RDWR)
		if (ttyFd >= 0) fcntl(ttyFd, F_SETFD, FD_CLOEXEC)
	}
	return ttyFd
}


@OptIn(ExperimentalForeignApi::class)
actual fun stdoutIsTty() = isatty(STDOUT_FILENO) == 1

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
		if (!stdinExhausted || isatty(STDIN_FILENO) == 1) {
			var readErr = false
			val input = try {
				readlnOrNull()
			} catch (_: Exception) {
				clearerr(stdin)
				readErr = true
				null
			}
			if (input != null) {
				if (isatty(STDIN_FILENO) != 1) println(input)
				return input
			}
			if (readErr) {
				val fd = ensureTty()
				if (fd >= 0) return readTtyLine(fd)
				println(); fflush(null)
				return ""
			}
			if (isatty(STDIN_FILENO) != 1) stdinExhausted = true
			if (isatty(STDIN_FILENO) == 1) {
				clearerr(stdin)
				return ""
			}
		}
		val fd = ensureTty()
		if (fd >= 0) return readTtyLine(fd)
		println(); fflush(null)
		return ""
	}
	
	if (!stdinExhausted || isatty(STDIN_FILENO) == 1) {
		if (isatty(STDIN_FILENO) == 1) return readPasswordTty(STDIN_FILENO)
		
		val (password, hitEof) = readPasswordPipe()
		if (!hitEof) return password
		
		stdinExhausted = true
	}
	val fd = ensureTty()
	if (fd >= 0) return readPasswordTty(fd)
	println(); fflush(null)
	return ""
}


@OptIn(ExperimentalForeignApi::class)
private fun readTtyLine(fd: Int): String {
	val bytes = mutableListOf<Byte>()
	
	memScoped {
		val buf = allocArray<ByteVar>(1)
		while (true) {
			val n = read(fd, buf, 1U)
			if (n < 0 && errno == EINTR) continue
			if (n <= 0) break
			val byte = buf[0]
			if (byte == '\n'.code.toByte() || byte == '\r'.code.toByte()) break
			bytes.add(byte)
		}
	}
	return bytes.toByteArray().decodeToString()
}


private sealed class ByteResult {
	data class Ok(val value: Int) : ByteResult()
	object Eof : ByteResult()
	object Retry : ByteResult()
}


@OptIn(ExperimentalForeignApi::class)
private fun readPasswordByteLoop(nextByte: () -> ByteResult): Pair<List<Byte>, Boolean> {
	val bytes = mutableListOf<Byte>()
	var sawAnyChar = false
	var escSeen = false
	var bracketSeen = false
	
	while (true) {
		when (val result = nextByte()) {
			is ByteResult.Eof -> return Pair(stripIncompleteUtf8(bytes), !sawAnyChar)
			is ByteResult.Retry -> {}
			is ByteResult.Ok -> {
				val ch = result.value
				sawAnyChar = true
				
				if (escSeen) {
					escSeen = false
					if (ch == '['.code || ch == 'O'.code) {
						bracketSeen = true; continue
					}
				}
				if (bracketSeen) {
					if (ch in 0x20..0x3F) continue
					bracketSeen = false
					if (ch in 0x40..0x7E) continue
				}
				
				when (ch) {
					'\n'.code, '\r'.code -> return Pair(stripIncompleteUtf8(bytes), false)
					0x04 -> {
						bytes.clear(); return Pair(emptyList(), false)
					}
					
					0x1B -> {
						escSeen = true
					}
					
					127, 8 -> if (bytes.isNotEmpty()) {
						removeLastUtf8Char(bytes)
						print("\b \b"); fflush(null)
					}
					
					else -> if (ch in 32..126 || ch >= 128) {
						bytes.add(ch.toByte())
						if (isUtf8Boundary(bytes)) {
							print("*"); fflush(null)
						}
					}
				}
			}
		}
	}
}


@OptIn(ExperimentalForeignApi::class)
private fun readPasswordPipe(): Pair<String, Boolean> {
	val (bytes, hitEof) = readPasswordByteLoop {
		val ch = getchar()
		if (ch == -1) ByteResult.Eof
		else ByteResult.Ok(ch)
	}
	if (!hitEof) {
		println(); fflush(null)
	}
	return Pair(bytes.toByteArray().decodeToString(), hitEof)
}


private var sigintTermiosFd: Int = -1

@OptIn(ExperimentalForeignApi::class)
private var sigintTermiosPtr: CPointer<termios>? = null

@OptIn(ExperimentalForeignApi::class)
private val sigintHandlerPtr = staticCFunction { _: Int ->
	val fd = sigintTermiosFd
	val ptr = sigintTermiosPtr
	if (fd >= 0 && ptr != null) {
		tcsetattr(fd, TCSANOW, ptr)
	}
	_exit(128 + SIGINT)
}


@OptIn(ExperimentalForeignApi::class)
private fun readPasswordTty(fd: Int): String = memScoped {
	tcflush(fd, TCIFLUSH)
	
	val t = alloc<termios>()
	val saved = alloc<termios>()
	tcgetattr(fd, saved.ptr)
	tcgetattr(fd, t.ptr)
	t.c_lflag = t.c_lflag and ECHO.toUInt().inv()
	t.c_lflag = t.c_lflag and ICANON.toUInt().inv()
	tcsetattr(fd, TCSANOW, t.ptr)
	
	sigintTermiosFd = fd
	sigintTermiosPtr = saved.ptr
	signal(SIGINT, sigintHandlerPtr)
	
	val buf = allocArray<ByteVar>(1)
	val (bytes, _) = try {
		readPasswordByteLoop {
			val n = read(fd, buf, 1U)
			if (n < 0 && errno == EINTR) ByteResult.Retry
			else if (n <= 0) ByteResult.Eof
			else ByteResult.Ok(buf[0].toInt() and 0xFF)
		}
	} finally {
		signal(SIGINT, SIG_DFL)
		sigintTermiosFd = -1
		sigintTermiosPtr = null
		tcsetattr(fd, TCSANOW, saved.ptr)
		println(); fflush(null)
	}
	bytes.toByteArray().decodeToString()
}


private fun isUtf8Boundary(bytes: List<Byte>): Boolean {
	if (bytes.isEmpty()) return true
	val last = bytes.last().toInt() and 0xFF
	if (last < 0x80) return true
	if ((last and 0xC0) == 0x80) {
		var pos = bytes.size - 2
		while (pos >= 0 && (bytes[pos].toInt() and 0xC0) == 0x80) pos--
		if (pos < 0) return false
		val start = bytes[pos].toInt() and 0xFF
		val expected = when {
			(start and 0xE0) == 0xC0 -> 2
			(start and 0xF0) == 0xE0 -> 3
			(start and 0xF8) == 0xF0 -> 4
			else -> 1
		}
		return (bytes.size - pos) >= expected
	}
	return false
}


private fun removeLastUtf8Char(bytes: MutableList<Byte>) {
	if (bytes.isEmpty()) return
	val removed = bytes.removeLast().toInt() and 0xFF
	if ((removed and 0xC0) != 0x80) return
	while (bytes.isNotEmpty() && (bytes.last().toInt() and 0xC0) == 0x80) {
		bytes.removeLast()
	}
	if (bytes.isNotEmpty()) bytes.removeLast()
}


private fun stripIncompleteUtf8(bytes: List<Byte>): List<Byte> {
	var end = bytes.size
	while (end > 0 && (bytes[end - 1].toInt() and 0xC0) == 0x80) end--
	if (end == 0) return emptyList()
	val start = bytes[end - 1].toInt() and 0xFF
	val actualLen = bytes.size - end + 1
	val expectedLen = when {
		(start and 0xE0) == 0xC0 -> 2
		(start and 0xF0) == 0xE0 -> 3
		(start and 0xF8) == 0xF0 -> 4
		else -> 1
	}
	return if (actualLen < expectedLen) bytes.subList(0, end - 1) else bytes
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
