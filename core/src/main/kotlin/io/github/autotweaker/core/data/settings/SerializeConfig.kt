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

package io.github.autotweaker.core.data.settings

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds

object SerializeConfig {
	private val log = LoggerFactory.getLogger(SerializeConfig::class.java)
	
	private val baseUrl: String
		get() = System.getenv("AUTOTWEAKER_WEBSITE_URL") ?: error("AUTOTWEAKER_WEBSITE_URL not set")
	
	private const val CONFIG_VERSION = 1
	
	private val json = Json {
		prettyPrint = false
		ignoreUnknownKeys = true
	}
	
	private var cachedItems: List<SettingItem>? = null
	
	suspend fun fetchDefaultConfig(): List<SettingItem> {
		cachedItems?.let { return it }
		
		val items = retry { fetchFromRemote() }
		cachedItems = items
		return items
	}
	
	private suspend fun fetchFromRemote(): List<SettingItem> {
		val proxyUrl = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")
		val client = if (baseUrl.startsWith("http") && proxyUrl != null) {
			val uri = URI(proxyUrl)
			val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
			HttpClient(CIO) {
				engine {
					this.proxy = proxy
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
			val index = json.decodeFromString<RootIndex>(fetch(httpClient, "index.json"))
			
			val configVersion = index.defaultAppConfig.version.toInt()
			require(configVersion == CONFIG_VERSION) {
				"Config version mismatch  site=$configVersion  local=$CONFIG_VERSION"
			}
			
			val configResponse = fetch(httpClient, index.defaultAppConfig.url)
			return json.decodeFromString(ListSerializer(SettingItem.serializer()), configResponse)
		}
	}
	
	private suspend fun fetch(httpClient: HttpClient?, path: String): String {
		return httpClient?.get(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))?.body() ?: Path.of(baseUrl, path)
			.readText()
	}
	
	private suspend fun <T> retry(times: Int = 10, delayMs: Long = 2000, block: suspend () -> T): T {
		var last: Throwable? = null
		repeat(times) { attempt ->
			try {
				return block()
			} catch (e: Exception) {
				last = e
				if (attempt < times - 1) {
					log.warn("Retried config fetch  attempt={}/{}  reason={}", attempt + 1, times - 1, e.message)
					delay(delayMs.milliseconds)
				}
			}
		}
		throw last!!
	}
	
	@Serializable
	private data class RootIndex(
		@SerialName("default_app_config") val defaultAppConfig: DefaultAppConfig,
		@SerialName("projects_version") val projectsVersion: ProjectsVersion,
	)
	
	@Serializable
	private data class DefaultAppConfig(
		val url: String,
		val version: String = "",
	)
	
	@Serializable
	private data class ProjectsVersion(
		val core: String,
	)
}
