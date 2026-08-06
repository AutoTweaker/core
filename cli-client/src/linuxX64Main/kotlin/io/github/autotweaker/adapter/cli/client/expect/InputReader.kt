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

import io.github.autotweaker.api.MASK_CHAR
import kotlinx.cinterop.*
import platform.posix.*


object InputReader {
	private const val MAX_PASSWORD_BYTES = 1024
	private const val ESC_TIMEOUT_MS = 300
	
	var stdinExhausted = false
	
	@OptIn(ExperimentalForeignApi::class)
	fun readTtyLine(fd: Int): String {
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
		return stripIncompleteUtf8(bytes).toByteArray().decodeToString()
	}
	
	fun readTtyFallback(): String {
		val fd = Terminal.ensureTty()
		if (fd >= 0) return readTtyLine(fd)
		printErr("\n")
		return ""
	}
	
	fun readPasswordFallback(): String {
		val fd = Terminal.ensureTty()
		if (fd >= 0) return readPasswordTty(fd)
		printErr("\n")
		return ""
	}
	
	@OptIn(ExperimentalForeignApi::class)
	fun readPasswordTty(fd: Int): String {
		val session = Terminal.beginRaw(fd)
		if (session == null) {
			printErr("\n")
			return ""
		}
		return memScoped {
			val buf = allocArray<ByteVar>(1)
			val pollFds = alloc<pollfd>()
			fun readByte(): ByteResult {
				val n = read(fd, buf, 1U)
				if (n < 0 && errno == EINTR) return ByteResult.Retry
				if (n <= 0) return ByteResult.Eof
				return ByteResult.Ok(buf[0].toInt() and 0xFF)
			}
			val (bytes, _) = try {
				readPasswordByteLoop(
					nextByte = { readByte() },
					awaitByte = {
						pollFds.fd = fd
						pollFds.events = POLLIN.toShort()
						if (poll(pollFds.ptr, 1uL, ESC_TIMEOUT_MS) <= 0) ByteResult.Retry
						else readByte()
					},
				)
			} finally {
				Terminal.endRaw(session)
				printErr("\n")
			}
			bytes.toByteArray().decodeToString()
		}
	}
	
	fun readPasswordPipe(): Pair<String, Boolean> {
		val (bytes, hitEof) = readPasswordByteLoop(nextByte = {
			val ch = getchar()
			if (ch == -1) ByteResult.Eof
			else ByteResult.Ok(ch)
		})
		if (!hitEof) {
			printErr("\n")
		}
		return Pair(bytes.toByteArray().decodeToString(), hitEof)
	}
	
	private sealed class ByteResult {
		data class Ok(val value: Int) : ByteResult()
		object Eof : ByteResult()
		object Retry : ByteResult()
	}
	
	private fun readPasswordByteLoop(
		nextByte: () -> ByteResult,
		awaitByte: (() -> ByteResult)? = null,
	): Pair<List<Byte>, Boolean> {
		val bytes = mutableListOf<Byte>()
		var sawAnyChar = false
		var escSeen = false
		var bracketSeen = false
		
		while (true) {
			val result = if (escSeen && awaitByte != null) awaitByte() else nextByte()
			when (result) {
				is ByteResult.Eof -> return Pair(stripIncompleteUtf8(bytes), !sawAnyChar)
				is ByteResult.Retry -> if (escSeen && awaitByte != null) escSeen = false
				is ByteResult.Ok -> {
					val ch = result.value
					sawAnyChar = true
					
					if (escSeen) {
						escSeen = false
						if (ch == '['.code || ch == 'O'.code) {
							bracketSeen = true; continue
						}
						bytes.add(0x1B.toByte())
						if (bytes.size > MAX_PASSWORD_BYTES) return Pair(stripIncompleteUtf8(bytes), true)
					}
					if (bracketSeen) {
						if (ch in 0x20..0x3F) continue
						bracketSeen = false
						if (ch in 0x40..0x7E) continue
					}
					
					when (ch) {
						'\n'.code, '\r'.code -> return Pair(stripIncompleteUtf8(bytes), false)
						0x04 -> {}
						
						0x1B -> {
							escSeen = true
						}
						
						127, 8 -> if (bytes.isNotEmpty()) {
							removeLastUtf8Char(bytes)
							printErr("\b \b")
						}
						
						else -> if (ch in 32..126 || ch >= 128) {
							bytes.add(ch.toByte())
							if (bytes.size > MAX_PASSWORD_BYTES) return Pair(stripIncompleteUtf8(bytes), true)
							if (isUtf8Boundary(bytes)) {
								printErr(MASK_CHAR.toString())
							}
						}
					}
				}
			}
		}
	}
	
	private fun utf8ExpectedLen(lead: Int): Int = when {
		(lead and 0xE0) == 0xC0 -> 2
		(lead and 0xF0) == 0xE0 -> 3
		(lead and 0xF8) == 0xF0 -> 4
		else -> 1
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
			return (bytes.size - pos) >= utf8ExpectedLen(start)
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
		return if (actualLen < utf8ExpectedLen(start)) bytes.subList(0, end - 1) else bytes
	}
}
