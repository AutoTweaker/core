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
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import java.util.*
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds

object I18nLoader {
	private val logger = LoggerFactory.getLogger(I18nLoader::class.java)
	
	private val baseUrl: String
		get() = System.getenv("AUTOTWEAKER_WEBSITE_URL") ?: error("AUTOTWEAKER_WEBSITE_URL not set")
	
	private val json = Json { ignoreUnknownKeys = true }
	private var cachedBundle: ResourceBundle? = null
	
	suspend fun fetchBundle(component: String): ResourceBundle? {
		cachedBundle?.let { return it }
		
		return runCatching {
			withContext(Dispatchers.IO) {
				retry(component) { downloadBundle(component) }
			}
		}.onSuccess { _ ->
			logger.info("I18n bundle loaded  component={}", component)
		}.onFailure { e ->
			logger.warn("Failed to load i18n bundle  component={}  reason={}", component, e.message)
		}.getOrNull()
	}
	
	private suspend fun downloadBundle(component: String): ResourceBundle {
		val proxyUrl = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")
		val client = if (baseUrl.startsWith("http") && proxyUrl != null) {
			val uri = URI(proxyUrl)
			HttpClient(CIO) {
				engine {
					proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
					requestTimeout = 45_000
				}
			}
		} else if (baseUrl.startsWith("http")) {
			HttpClient(CIO) {
				engine { requestTimeout = 45_000 }
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
			val bundle = PropertyResourceBundle(ByteArrayInputStream(content.toByteArray()))
			cachedBundle = bundle
			return bundle
		}
	}
	
	private suspend fun fetch(httpClient: HttpClient?, path: String): String {
		return httpClient?.get(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))?.body() ?: Path.of(baseUrl, path)
			.readText()
	}
	
	private suspend fun <T> retry(component: String, times: Int = 3, delayMs: Long = 2000, block: suspend () -> T): T {
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
