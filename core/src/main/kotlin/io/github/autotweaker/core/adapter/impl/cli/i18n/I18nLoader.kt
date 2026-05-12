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

package io.github.autotweaker.core.adapter.impl.cli.i18n

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

object I18nLoader {
	private val logger = LoggerFactory.getLogger(I18nLoader::class.java)
	
	private val baseUrl: String
		get() = System.getenv("AUTOTWEAKER_WEBSITE_URL") ?: error("AUTOTWEAKER_WEBSITE_URL not set")
	
	private val json = Json { ignoreUnknownKeys = true }
	private var cachedBundle: ResourceBundle? = null
	
	private val cacheDir: Path = Path.of(
		System.getProperty("user.home"), ".config", "autotweaker", "i18n"
	)
	
	fun fetchBundle(component: String, onUpdate: (ResourceBundle) -> Unit): ResourceBundle? {
		val local = cachedBundle ?: loadFromCache(component)
		if (local != null) {
			cachedBundle = local
			logger.debug("I18n bundle loaded from cache  component={}", component)
		}
		
		Thread({
			runBlocking {
				runCatching {
					withContext(Dispatchers.IO) {
						retry(component) { downloadBundle(component) }
					}
				}.onSuccess { bundle ->
					cachedBundle = bundle
					onUpdate(bundle)
					logger.info("I18n bundle loaded  component={}", component)
				}.onFailure { e ->
					logger.warn("Failed to load i18n bundle  component={}  reason={}", component, e.message)
				}
			}
		}, "i18n-update").apply { isDaemon = true }.start()
		
		return local
	}
	
	private fun loadFromCache(component: String): ResourceBundle? = runCatching {
		val cacheFile = cacheDir.resolve("$component.properties")
		if (cacheFile.notExists()) return null
		val content = cacheFile.readText()
		PropertyResourceBundle(ByteArrayInputStream(content.toByteArray()))
	}.onFailure { e ->
		logger.debug("Failed to load i18n cache  component={}  reason={}", component, e.message)
	}.getOrNull()
	
	private suspend fun downloadBundle(component: String): ResourceBundle {
		val proxyUrl = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")
		val client = if (baseUrl.startsWith("http") && proxyUrl != null) {
			val uri = URI(proxyUrl)
			HttpClient(CIO) {
				engine {
					proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
				}
				install(HttpTimeout) {
					connectTimeoutMillis = 5_000
					requestTimeoutMillis = 15_000
				}
			}
		} else if (baseUrl.startsWith("http")) {
			HttpClient(CIO) {
				install(HttpTimeout) {
					connectTimeoutMillis = 5_000
					requestTimeoutMillis = 15_000
				}
			}
		} else {
			null
		}
		
		client.use { httpClient ->
			val root = json.decodeFromString<RootIndex>(fetch(httpClient, "index.json"))
			val i18nIndex = json.decodeFromString<Map<String, List<String>>>(fetch(httpClient, root.i18nIndex))
			val urls = i18nIndex[component]
			if (urls == null) {
				logger.warn("No i18n entries for component  component={}", component)
				return ResourceBundle.getBundle("")
			}
			
			val locale = Locale.getDefault()
			val localeTag = "_$locale"
			val url = urls.firstOrNull { it.endsWith(".properties") && it.contains(localeTag) }
				?: urls.firstOrNull { it.endsWith("messages.properties") }
			if (url == null) {
				logger.warn("No matching i18n file found  component={}  locale={}", component, locale)
				return ResourceBundle.getBundle("")
			}
			
			val content = fetch(httpClient, url)
			saveToCache(component, content)
			return PropertyResourceBundle(ByteArrayInputStream(content.toByteArray()))
		}
	}
	
	private fun saveToCache(component: String, content: String) {
		runCatching {
			Files.createDirectories(cacheDir)
			cacheDir.resolve("$component.properties").writeText(content)
		}.onFailure { e ->
			logger.debug("Failed to save i18n cache  component={}  reason={}", component, e.message)
		}
	}
	
	private suspend fun fetch(httpClient: HttpClient?, path: String): String {
		return httpClient?.get(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))?.body() ?: Path.of(baseUrl, path)
			.readText()
	}
	
	private suspend fun <T> retry(component: String, times: Int = 10, delayMs: Long = 2000, block: suspend () -> T): T {
		var last: Throwable? = null
		repeat(times) { attempt ->
			try {
				return block()
			} catch (e: Exception) {
				last = e
				if (attempt < times - 1) {
					logger.warn(
						"Retried i18n bundle retrieval  component={}  attempt={}/{}  reason={}",
						component,
						attempt + 1,
						times,
						e.message
					)
					delay(delayMs.milliseconds)
				}
			}
		}
		throw last!!
	}
	
	@Serializable
	private data class RootIndex(
		@SerialName("i18n_index") val i18nIndex: String,
	)
}
