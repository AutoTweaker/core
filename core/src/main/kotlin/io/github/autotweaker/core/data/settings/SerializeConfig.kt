package io.github.autotweaker.core.data.settings

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

object SerializeConfig {
	private const val INDEX_URL = "https://autotweaker.github.io/website/"

	private const val SETTINGS_VERSION = "dev"

	private val json = Json {
		prettyPrint = false
		ignoreUnknownKeys = true
	}
	
	private var cachedItems: List<SettingItem>? = null
	
	suspend fun fetchDefaultConfig(): List<SettingItem> {
		cachedItems?.let { return it }
		
		val proxyUrl = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")
		val client = if (proxyUrl != null) {
			val uri = URI(proxyUrl)
			val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
			HttpClient(CIO) {
				engine {
					this.proxy = proxy
				}
			}
		} else {
			HttpClient(CIO)
		}
		
		client.use { httpClient ->
			val indexResponse: String = httpClient.get(INDEX_URL).body()
			val jsonArray = json.parseToJsonElement(indexResponse).jsonArray
			val index = jsonArray.mapNotNull { element ->
				try {
					json.decodeFromJsonElement<WebsiteIndex>(element)
				} catch (e: Exception) {
					null
				}
			}.first { it.defaultAppConfig.isNotBlank() }

			require(index.version == SETTINGS_VERSION) {
				"Expected version '$SETTINGS_VERSION', but got '${index.version}'"
			}
			val configResponse: String = httpClient.get(index.defaultAppConfig).body()
			val items = json.decodeFromString(ListSerializer(SettingItem.serializer()), configResponse)
			cachedItems = items
			return items
		}
	}
	
	@kotlinx.serialization.Serializable
	private data class WebsiteIndex(
		@kotlinx.serialization.SerialName("default_app_config")
		val defaultAppConfig: String,
		val version: String
	)
}
