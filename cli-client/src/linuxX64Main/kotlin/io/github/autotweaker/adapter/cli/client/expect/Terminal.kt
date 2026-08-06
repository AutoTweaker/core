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

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import platform.posix.*
import kotlin.concurrent.Volatile
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker


object Terminal {
	var ttyFd = -1
	var echoRequested = true
	var passwordFd = -1
	
	@OptIn(ExperimentalForeignApi::class)
	private var savedStdinTermios: CValue<termios>? = null
	
	@OptIn(ExperimentalForeignApi::class)
	private var savedTtyTermios: CValue<termios>? = null
	
	private var stdinOursFlags = 0u
	
	private var ttyOursFlags = 0u
	
	@OptIn(ExperimentalForeignApi::class)
	private val termiosScratch: CPointer<termios> = nativeHeap.alloc<termios>().ptr
	
	private val fatalSignals = intArrayOf(
		SIGTERM, SIGQUIT, SIGHUP, SIGPIPE, SIGSEGV, SIGABRT, SIGBUS, SIGFPE,
		SIGUSR1, SIGUSR2, SIGALRM, SIGXCPU, SIGXFSZ,
	)
	
	@OptIn(ExperimentalForeignApi::class)
	class RawSession(
		val fd: Int,
		val saved: CValue<termios>,
		val ours: UInt,
	)
	
	@OptIn(ExperimentalForeignApi::class)
	private val interruptHandlerPtr: CPointer<CFunction<(Int) -> Unit>> = staticCFunction { _: Int ->
		restoreInteractiveSafe()
		_exit(128 + SIGINT)
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private val fatalHandlerPtr: CPointer<CFunction<(Int) -> Unit>> = staticCFunction { sig: Int ->
		restoreInteractiveSafe()
		signal(sig, SIG_DFL)
		raise(sig)
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private val stopHandlerPtr: CPointer<CFunction<(Int) -> Unit>> = staticCFunction { _: Int ->
		restoreInteractiveSafe()
		signal(SIGTSTP, SIG_DFL)
		raise(SIGTSTP)
		signal(SIGTSTP, stopHandlerPtr)
		if (passwordFd >= 0) applyRawSafe(passwordFd)
		else setEchoSafe(if (ttyFd >= 0) ttyFd else STDIN_FILENO, echoRequested)
	}
	
	fun ensureTty(): Int {
		if (ttyFd < 0) {
			ttyFd = open("/dev/tty", O_RDWR)
			if (ttyFd >= 0) fcntl(ttyFd, F_SETFD, FD_CLOEXEC)
		}
		return ttyFd
	}
	
	fun interactiveFd(): Int = if (isatty(STDIN_FILENO) == 1) STDIN_FILENO else ensureTty()
	
	val resizeChannel = Channel<Int>(Channel.CONFLATED)
	
	@Volatile
	private var watcherStarted = false
	
	@OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class)
	fun startResizeWatcher() {
		if (watcherStarted) return
		watcherStarted = true
		val worker = Worker.start()
		worker.execute(TransferMode.SAFE, { }) {
			memScoped {
				val set = alloc<sigset_t>()
				sigemptyset(set.ptr)
				sigaddset(set.ptr, SIGWINCH)
				sigprocmask(SIG_BLOCK, set.ptr, null)
				val sig = alloc<IntVar>()
				while (true) {
					sigwait(set.ptr, sig.ptr)
					val cols = windowCols()
					if (cols > 0) resizeChannel.trySend(cols)
				}
			}
		}
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun windowCols(): Int = memScoped {
		val ws = allocArray<UShortVar>(4)
		if (ioctl(STDOUT_FILENO, TIOCGWINSZ.toULong(), ws.reinterpret<ByteVar>()) == 0) ws[1].toInt() else 0
	}
	
	private const val TIOCGWINSZ = 0x5413
	
	@OptIn(ExperimentalForeignApi::class)
	fun beginNoEcho() {
		signal(SIGINT, interruptHandlerPtr)
		signal(SIGTSTP, stopHandlerPtr)
		fatalSignals.forEach { signal(it, fatalHandlerPtr) }
		if (isatty(STDIN_FILENO) == 1) setEcho(STDIN_FILENO, false)
		else {
			val fd = ensureTty()
			if (fd >= 0) setEcho(fd, false)
		}
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun endNoEcho() {
		restoreTermios(STDIN_FILENO, savedStdinTermios, stdinOursFlags)
		if (ttyFd >= 0) restoreTermios(ttyFd, savedTtyTermios, ttyOursFlags)
		echoRequested = true
		passwordFd = -1
		signal(SIGINT, SIG_DFL)
		signal(SIGTSTP, SIG_DFL)
		fatalSignals.forEach { signal(it, SIG_DFL) }
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun setEcho(fd: Int, enable: Boolean) {
		echoRequested = enable
		if (isatty(fd) != 1) return
		if (fd == STDIN_FILENO) {
			if (savedStdinTermios == null) savedStdinTermios = saveTermios(fd)
			stdinOursFlags = echoBits(fd, enable) ?: return
		} else {
			if (savedTtyTermios == null) savedTtyTermios = saveTermios(fd)
			ttyOursFlags = echoBits(fd, enable) ?: return
		}
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun flushStdin() {
		tcflush(STDIN_FILENO, TCIFLUSH)
		if (isatty(STDIN_FILENO) == 1) fflush(stdin)
		if (ttyFd >= 0) tcflush(ttyFd, TCIFLUSH)
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun beginRaw(fd: Int): RawSession? {
		val saved = saveTermios(fd) ?: return null
		val ours = applyRaw(fd, saved) ?: return null
		if (fd == STDIN_FILENO) stdinOursFlags = ours
		else ttyOursFlags = ours
		passwordFd = fd
		return RawSession(fd, saved, ours)
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun endRaw(session: RawSession) {
		passwordFd = -1
		if (restoreTermios(session.fd, session.saved, session.ours)) {
			if (session.fd == STDIN_FILENO) stdinOursFlags = flagsOf(session.saved)
			else ttyOursFlags = flagsOf(session.saved)
		}
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun saveTermios(fd: Int): CValue<termios>? = memScoped {
		val t = alloc<termios>()
		if (tcgetattr(fd, t.ptr) != 0) null else t.readValue()
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun flagsOf(saved: CValue<termios>): UInt = memScoped {
		val t = alloc<termios>()
		saved.place(t.ptr)
		t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun echoBits(fd: Int, enable: Boolean): UInt? = memScoped {
		val t = alloc<termios>()
		if (tcgetattr(fd, t.ptr) != 0) return null
		t.c_lflag = if (enable) t.c_lflag or ECHO.toUInt() else t.c_lflag and ECHO.toUInt().inv()
		if (tcsetattr(fd, TCSANOW, t.ptr) != 0) return null
		t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun applyRaw(fd: Int, saved: CValue<termios>): UInt? = memScoped {
		val t = alloc<termios>()
		saved.place(t.ptr)
		t.c_lflag = t.c_lflag and ECHO.toUInt().inv() and ICANON.toUInt().inv()
		if (tcsetattr(fd, TCSANOW, t.ptr) != 0) return null
		t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun restoreTermios(fd: Int, saved: CValue<termios>?, ours: UInt): Boolean {
		if (saved == null) return false
		memScoped {
			val t = alloc<termios>()
			if (tcgetattr(fd, t.ptr) != 0) return false
			if (t.c_lflag and (ECHO.toUInt() or ICANON.toUInt()) != ours) return false
			saved.place(t.ptr)
			if (tcsetattr(fd, TCSANOW, t.ptr) != 0) return false
		}
		return true
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun restoreTermiosSafe(fd: Int, saved: CValue<termios>?, ours: UInt) {
		if (saved == null) return
		if (tcgetattr(fd, termiosScratch) != 0) return
		if (termiosScratch.pointed.c_lflag and (ECHO.toUInt() or ICANON.toUInt()) != ours) return
		saved.place(termiosScratch)
		if (tcsetattr(fd, TCSANOW, termiosScratch) != 0) return
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun restoreInteractiveSafe() {
		restoreTermiosSafe(STDIN_FILENO, savedStdinTermios, stdinOursFlags)
		if (ttyFd >= 0) restoreTermiosSafe(ttyFd, savedTtyTermios, ttyOursFlags)
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun setEchoSafe(fd: Int, enable: Boolean) {
		if (isatty(fd) != 1) return
		if (tcgetattr(fd, termiosScratch) != 0) return
		val t = termiosScratch.pointed
		t.c_lflag = if (enable) t.c_lflag or ECHO.toUInt() else t.c_lflag and ECHO.toUInt().inv()
		if (tcsetattr(fd, TCSANOW, termiosScratch) != 0) return
		if (fd == STDIN_FILENO) stdinOursFlags = t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
		else ttyOursFlags = t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
	}
	
	@OptIn(ExperimentalForeignApi::class)
	private fun applyRawSafe(fd: Int) {
		if (tcgetattr(fd, termiosScratch) != 0) return
		val t = termiosScratch.pointed
		t.c_lflag = t.c_lflag and ECHO.toUInt().inv() and ICANON.toUInt().inv()
		if (tcsetattr(fd, TCSANOW, termiosScratch) != 0) return
		if (fd == STDIN_FILENO) stdinOursFlags = t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
		else ttyOursFlags = t.c_lflag and (ECHO.toUInt() or ICANON.toUInt())
	}
}
