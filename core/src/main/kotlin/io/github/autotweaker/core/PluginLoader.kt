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

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.PLUGIN_PATH
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader
import org.objectweb.asm.ClassReader
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile

object PluginLoader : Loggable, Traceable {
	@Volatile
	var sharedClassLoader: URLClassLoader? = null
	
	fun getOrCreateClassLoader(apiClassLoader: ClassLoader): URLClassLoader? {
		sharedClassLoader?.let { return it }
		synchronized(this) {
			sharedClassLoader?.let { return it }
			if (!Files.isDirectory(PLUGIN_PATH)) return null
			
			val jars = Files.list(PLUGIN_PATH).use {
				it.filter { path -> path.toString().endsWith(".jar") }.toList()
			}
			if (jars.isEmpty()) return null
			
			val urls = jars.mapNotNull { path ->
				runCatching { //加载StartupHook时trace尚未准备好
					JarFile(path.toFile()).use { jar ->
						jar.entries().asSequence()
							.filter { it.name.endsWith(".class") }
							.forEach { entry ->
								jar.getInputStream(entry).use { stream ->
									ClassReader(stream.readAllBytes())
								}
							}
						if (isApiCompatible(jar, path)) path.toUri().toURL() else null
					}
				}
					.onFailure { log.warn("Skipping bad plugin jar  path={}  reason={}", path, it.message) }
					.getOrNull()
			}.toTypedArray()
			val classLoader = PluginClassLoader(urls, apiClassLoader)
			log.info("Created shared plugin classLoader  jarCount={}  classLoader={}", urls.size, classLoader)
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
	
	fun close() = sharedClassLoader?.close()
	
	private fun isApiCompatible(jar: JarFile, path: Path): Boolean {
		val raw = jar.getJarEntry("META-INF/autotweaker/plugin.properties")?.let { entry ->
			jar.getInputStream(entry).use { stream ->
				Properties().apply { load(stream) }.getProperty("apiVersion")
			}
		}
		if (raw == null) {
			log.warn("Skipped plugin jar missing api version  path={}", path)
			return false
		}
		val declared = SemVer.parse(raw)
		val core = ResourcesLoader.version
		val sameVersion = declared.major == core.major && declared.minor == core.minor && declared.patch == core.patch
		if (sameVersion && core.preRelease.singleOrNull() == "dev") return true
		if (declared > core) {
			log.warn("Skipped plugin jar newer than core  path={}  apiVersion={}  coreVersion={}", path, declared, core)
			return false
		}
		if (declared == core) return true
		if (core.major > 0 && declared.major == core.major) {
			log.warn(
				"Plugin jar built against a different api version  path={}  apiVersion={}  coreVersion={}",
				path,
				declared,
				core
			)
			return true
		}
		log.warn(
			"Skipped plugin jar incompatible with core  path={}  apiVersion={}  coreVersion={}",
			path,
			declared,
			core
		)
		return false
	}
	
	private class PluginClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
		override fun getResources(name: String): Enumeration<URL> =
			if (name.startsWith("META-INF/services/")) findResources(name)
			else super.getResources(name)
	}
}
