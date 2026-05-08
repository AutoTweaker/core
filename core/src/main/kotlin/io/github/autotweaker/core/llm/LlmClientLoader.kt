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

package io.github.autotweaker.core.llm

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object LlmClientLoader {
	private val builtIn: List<LlmClient> by lazy {
		ServiceLoader.load(LlmClient::class.java).toList()
	}
	
	private val all: List<LlmClient> by lazy {
		val external = loadExternalProviders()
		val externalNames = external.map { it.providerInfo.name }.toSet()
		external + builtIn.filter { it.providerInfo.name !in externalNames }
	}
	
	private fun loadExternalProviders(): List<LlmClient> {
		val dir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "plugins", "provider")
		if (!Files.isDirectory(dir)) return emptyList()
		
		val jars = Files.list(dir).filter { it.toString().endsWith(".jar") }.toList()
		if (jars.isEmpty()) return emptyList()
		
		val urls = jars.map { it.toUri().toURL() }.toTypedArray()
		val classLoader = URLClassLoader(urls, LlmClientLoader::class.java.classLoader)
		return ServiceLoader.load(LlmClient::class.java, classLoader).toList()
	}
	
	fun load(name: String): LlmClient {
		return all.firstOrNull { it.providerInfo.name == name }
			?: throw IllegalArgumentException("Unknown LLM provider: $name")
	}
	
	fun availableProviders(): List<String> {
		return all.map { it.providerInfo.name }
	}
}