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

package io.github.autotweaker.core.infrastructure.llm

import io.github.autotweaker.api.llm.LlmClient
import io.github.autotweaker.core.PluginLoader
import org.slf4j.LoggerFactory
import java.util.*

object LlmClientLoader {
	private val logger = LoggerFactory.getLogger(this::class.java)
	
	private val builtIn: List<LlmClient> by lazy {
		ServiceLoader.load(LlmClient::class.java).toList()
	}
	
	private val all: List<LlmClient> by lazy {
		val external = PluginLoader.load<LlmClient>()
		val externalNames = external.map { it.providerInfo.name }.toSet()
		val result = external + builtIn.filter { it.providerInfo.name !in externalNames }
		logger.info(
			"LLM providers loaded  builtIn={}  external={}  total={}",
			builtIn.size,
			external.size,
			result.size
		)
		result
	}
	
	fun load(name: String): LlmClient {
		val client = all.firstOrNull { it.providerInfo.name == name }
			?: throw IllegalArgumentException("Unknown LLM provider: $name")
		logger.debug("LLM provider loaded  name={}", name)
		return client
	}
	
	fun availableProviders(): List<String> {
		return all.map { it.providerInfo.name }
	}
}
