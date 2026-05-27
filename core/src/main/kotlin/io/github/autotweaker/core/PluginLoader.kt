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

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

@PublishedApi
internal val pluginClassLoaders = Collections.synchronizedList(mutableListOf<URLClassLoader>())

inline fun <reified T : Any> loadPlugins(): List<T> {
	val dir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "plugins")
	if (!Files.isDirectory(dir)) return emptyList()
	
	val jars = Files.list(dir).filter { it.toString().endsWith(".jar") }.toList()
	if (jars.isEmpty()) return emptyList()
	
	val urls = jars.map { it.toUri().toURL() }.toTypedArray()
	val classLoader = URLClassLoader(urls, T::class.java.classLoader)
	pluginClassLoaders.add(classLoader)
	return ServiceLoader.load(T::class.java, classLoader).toList()
}

fun closePluginClassLoaders() {
	pluginClassLoaders.forEach { runCatching { it.close() } }
	pluginClassLoaders.clear()
}