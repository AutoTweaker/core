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

import io.github.autotweaker.adapter.cli.CliMessage
import io.github.autotweaker.adapter.cli.CliResponse
import io.github.autotweaker.adapter.cli.OutputChannel
import io.github.autotweaker.adapter.cli.client.expect.printErr
import io.github.autotweaker.adapter.cli.client.expect.promptOrStdin
import kotlinx.serialization.json.Json

object Protocol {
	private val json = Json { ignoreUnknownKeys = true }
	suspend operator fun invoke(transport: Transport): Int {
		while (true) {
			val line = transport.readLine() ?: break
			val response = try {
				json.decodeFromString<CliResponse>(line)
			} catch (e: Exception) {
				printErr("Error: failed to parse server response: ${e.message}\n")
				break
			}
			when (response) {
				is CliResponse.Data -> {
					when (response.channel) {
						OutputChannel.STDERR -> {
							printErr(response.text)
							if (response.newline) printErr("\n")
						}
						
						OutputChannel.STDOUT -> {
							print(response.text)
							if (response.newline) println()
						}
					}
				}
				
				is CliResponse.Done -> return response.exitCode
				is CliResponse.Prompt -> {
					val answer = promptOrStdin(response.text, response.echo)
					transport.sendLine(json.encodeToString(CliMessage.PromptResponse(answer)))
				}
			}
		}
		printErr("Error: server disconnected.")
		return 1
	}
}
