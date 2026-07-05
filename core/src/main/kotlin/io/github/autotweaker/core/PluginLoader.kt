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

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import org.objectweb.asm.ClassReader
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.*
import java.util.jar.JarFile

object PluginLoader : Loggable, Traceable {
	private val classLoaders = Collections.synchronizedList(mutableListOf<URLClassLoader>())
	
	@Volatile
	var sharedClassLoader: URLClassLoader? = null
	
	fun getOrCreateClassLoader(apiClassLoader: ClassLoader): URLClassLoader? {
		sharedClassLoader?.let { return it }
		synchronized(this) {
			sharedClassLoader?.let { return it }
			if (!Files.isDirectory(PLUGIN_PATH)) return URLClassLoader(emptyArray(), apiClassLoader)
			
			val jars = Files.list(PLUGIN_PATH).use {
				it.filter { path -> path.toString().endsWith(".jar") }.toList()
			}
			if (jars.isEmpty()) return null
			
			val urls = jars.mapNotNull { path ->
				trace.catching {
					JarFile(path.toFile()).use { jar ->
						jar.entries().asSequence()
							.filter { it.name.endsWith(".class") }
							.forEach { entry ->
								jar.getInputStream(entry).use { stream ->
									ClassReader(stream.readAllBytes())
								}
							}
					}
					path.toUri().toURL()
				}
					.onFailure { log.warn("Skipping bad plugin jar  path={}  reason={}", path, it.message) }
					.getOrNull()
			}.toTypedArray()
			val classLoader = URLClassLoader(urls, apiClassLoader)
			classLoaders.add(classLoader)
			log.info("Created shared plugin classLoader  jarCount={}  classLoader={}", jars.size, classLoader)
			sharedClassLoader = classLoader
			return classLoader
		}
	}
	
	inline fun <reified T : Any> load(): List<T> {
		val classLoader = getOrCreateClassLoader(T::class.java.classLoader) ?: return emptyList()
		val plugins = ServiceLoader.load(T::class.java, classLoader).toList()
		log.info("Loaded plugins  type={}  pluginCount={}", T::class.simpleName, plugins.size)
		return plugins
	}
	
	fun closeClassLoaders() {
		classLoaders.forEach { trace.catching { it.close() } }
		classLoaders.clear()
		sharedClassLoader = null
	}
}
