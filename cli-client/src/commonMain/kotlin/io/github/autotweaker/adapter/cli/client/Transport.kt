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
import kotlinx.io.files.Path

class Transport private constructor(
	private val selectorManager: SelectorManager,
	private val socket: Socket,
) : AutoCloseable {
	private val readChannel: ByteReadChannel = socket.openReadChannel()
	private val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
	
	suspend fun readLine(): String? = readChannel.readLineStrict()
	
	suspend fun sendLine(line: String) {
		writeChannel.writeStringUtf8(line + "\n")
	}
	
	override fun close() {
		socket.close()
		selectorManager.close()
	}
	
	companion object {
		suspend fun connect(path: Path): Transport {
			val selectorManager = SelectorManager(Dispatchers.Default)
			val socket = aSocket(selectorManager).tcp().connect(UnixSocketAddress(path.toString()))
			return Transport(selectorManager, socket)
		}
	}
}
