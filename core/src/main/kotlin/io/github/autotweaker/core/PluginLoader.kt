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
import io.github.autotweaker.api.types.SemVer
import org.objectweb.asm.ClassReader
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.system.exitProcess

object PluginLoader : Loggable, Traceable {
	private val sharedClassLoader: URLClassLoader by lazy {
		val jars = scan()
		if (jars.isEmpty()) {
			log.error("No plugin loaded")
			exitProcess(1)
		}
		val urls = jars.map { it.toUri().toURL() }.toTypedArray()
		PluginClassLoader(urls, javaClass.classLoader).also {
			log.info("Created shared plugin classLoader  jarCount={}  classLoader={}", urls.size, it)
		}
	}
	
	inline fun <reified T : Any> load(): List<T> = load(T::class.java)
	
	fun <T : Any> load(type: Class<T>): List<T> {
		val plugins = ServiceLoader.load(type, sharedClassLoader).toList()
		log.info("Loaded plugins  type={}  pluginCount={}", type.simpleName, plugins.size)
		return plugins
	}
	
	fun close() = sharedClassLoader.close()
	
	private fun scan(): List<Path> {
		val accepted = mutableListOf<Path>()
		val loadedIds = mutableSetOf<String>()
		PLUGIN_PATH.forEach { dir ->
			jarsOf(dir).asSequence()
				.mapNotNull(::readMetadata)
				.filter(::isApiCompatible)
				.groupBy { it.id }
				.map { (_, sameId) -> sameId.maxBy { it.version } }
				.forEach { metadata ->
					if (loadedIds.add(metadata.id)) {
						accepted.add(metadata.path)
					} else {
						log.warn("Skipped shadowed plugin  path={}  id={}", metadata.path, metadata.id)
					}
				}
		}
		return accepted
	}
	
	private fun jarsOf(dir: Path): List<Path> = if (Files.isDirectory(dir)) {
		Files.list(dir).use { paths ->
			paths.filter { it.toString().endsWith(".jar") }.sorted().toList()
		}
	} else emptyList()
	
	private fun readMetadata(path: Path): PluginMetadata? = runCatching { //加载StartupHook时trace尚未准备好
		JarFile(path.toFile()).use { jar ->
			jar.entries().asSequence()
				.filter { it.name.endsWith(".class") }
				.forEach { entry ->
					jar.getInputStream(entry).use { stream ->
						ClassReader(stream.readAllBytes())
					}
				}
			parseMetadata(jar, path)
		}
	}
		.onFailure { log.warn("Skipping bad plugin jar  path={}  reason={}", path, it.message()) }
		.getOrNull()
	
	private fun parseMetadata(jar: JarFile, path: Path): PluginMetadata? {
		val properties = jar.getJarEntry("META-INF/autotweaker/plugin.properties")?.let { entry ->
			jar.getInputStream(entry).use { stream -> Properties().apply { load(stream) } }
		}
		if (properties == null) {
			log.warn("Skipped plugin jar missing metadata  path={}", path)
			return null
		}
		val id = properties.getProperty("id")
		if (id == null) {
			log.warn("Skipped plugin jar missing id  path={}", path)
			return null
		}
		val version = readVersion(properties, "version", path) ?: return null
		val apiVersion = readVersion(properties, "apiVersion", path) ?: return null
		return PluginMetadata(path, id, version, apiVersion)
	}
	
	private fun readVersion(properties: Properties, key: String, path: Path): SemVer? {
		val raw = properties.getProperty(key)
		if (raw == null) {
			log.warn("Skipped plugin jar missing {}  path={}", key, path)
			return null
		}
		val parsed = runCatching { SemVer.parse(raw) }.getOrNull()
		if (parsed == null) log.warn("Skipped plugin jar with malformed {}  path={}  value={}", key, path, raw)
		return parsed
	}
	
	private fun isApiCompatible(metadata: PluginMetadata): Boolean {
		val declared = metadata.apiVersion
		val application = appVersion
		val sameVersion = declared.major == application.major &&
				declared.minor == application.minor &&
				declared.patch == application.patch
		if (sameVersion && application.preRelease.singleOrNull() == "dev") return true
		if (declared > application) {
			log.warn(
				"Skipped plugin jar newer than the application  path={}  apiVersion={}  appVersion={}",
				metadata.path,
				declared,
				application
			)
			return false
		}
		if (declared == application) return true
		if (application.major > 0 && declared.major == application.major) {
			log.warn(
				"Plugin jar built against a different api version  path={}  apiVersion={}  appVersion={}",
				metadata.path,
				declared,
				application
			)
			return true
		}
		log.warn(
			"Skipped plugin jar incompatible with the application  path={}  apiVersion={}  appVersion={}",
			metadata.path,
			declared,
			application
		)
		return false
	}
	
	private data class PluginMetadata(
		val path: Path,
		val id: String,
		val version: SemVer,
		val apiVersion: SemVer,
	)
	
	private class PluginClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
		override fun getResources(name: String): Enumeration<URL> =
			if (name.startsWith("META-INF/services/")) findResources(name)
			else super.getResources(name)
	}
}
