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

package io.github.autotweaker.adapter.cli.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.Path
import kotlinx.io.readByteArray

class Transport private constructor(
	private val selectorManager: SelectorManager,
	private val socket: Socket,
) : AutoCloseable {
	private val readChannel: ByteReadChannel = socket.openReadChannel()
	private val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
	private val writeLock = Mutex()
	
	suspend fun readLine(): String? {
		val collected = ByteChannel(false)
		val count = readChannel.readUntil(ByteString('\n'.code.toByte()), collected, ignoreMissing = true)
		if (count == 0L) return null
		collected.close()
		return collected.readRemaining().readByteArray().decodeToString()
	}
	
	suspend fun sendLine(line: String) = writeLock.withLock {
		writeChannel.writeStringUtf8(line + "\n")
	}
	
	override fun close() {
		socket.close()
		selectorManager.close()
	}
	
	companion object {
		suspend fun connect(path: Path): Transport {
			val selectorManager = SelectorManager(Dispatchers.Default)
			val socket = try {
				aSocket(selectorManager).tcp().connect(UnixSocketAddress(path.toString()))
			} catch (e: Exception) {
				selectorManager.close()
				throw e
			}
			return Transport(selectorManager, socket)
		}
	}
}
