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

package io.github.autotweaker.core

import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object PluginLoader {
	@PublishedApi
	internal val logger = LoggerFactory.getLogger(this::class.java)
	
	@PublishedApi
	internal val classLoaders = Collections.synchronizedList(mutableListOf<URLClassLoader>())
	
	@Volatile
	internal var sharedClassLoader: URLClassLoader? = null
	
	@PublishedApi
	internal fun getOrCreateClassLoader(apiClassLoader: ClassLoader): URLClassLoader {
		sharedClassLoader?.let { return it }
		synchronized(this) {
			sharedClassLoader?.let { return it }
			val dir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "plugins")
			if (!Files.isDirectory(dir)) return URLClassLoader(emptyArray(), apiClassLoader)
			
			val jars = Files.list(dir).filter { it.toString().endsWith(".jar") }.toList()
			if (jars.isEmpty()) return URLClassLoader(emptyArray(), apiClassLoader)
			
			val urls = jars.map { it.toUri().toURL() }.toTypedArray()
			val classLoader = URLClassLoader(urls, apiClassLoader)
			classLoaders.add(classLoader)
			logger.info("Created shared plugin classLoader  jarCount={}  classLoader={}", jars.size, classLoader)
			sharedClassLoader = classLoader
			return classLoader
		}
	}
	
	inline fun <reified T : Any> load(): List<T> {
		val classLoader = getOrCreateClassLoader(T::class.java.classLoader)
		val plugins = ServiceLoader.load(T::class.java, classLoader).toList()
		logger.info("Loaded plugins  type={}  pluginCount={}", T::class.simpleName, plugins.size)
		return plugins
	}
	
	fun closeClassLoaders() {
		classLoaders.forEach { runCatching { it.close() } }
		classLoaders.clear()
		sharedClassLoader = null
	}
}
