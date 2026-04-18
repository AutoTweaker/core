package io.github.autotweaker.core.data.settings

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object SerializeConfig {
    private const val INDEX_URL = "https://autotweaker.github.io/website/"

    const val SETTINGS_VERSION = "dev"

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private var cachedItems: List<SettingItem>? = null

    suspend fun fetchDefaultConfig(): List<SettingItem> {
        cachedItems?.let { return it }

        HttpClient(Java).use { client ->
            val indexResponse: String = client.get(INDEX_URL).body()
            val index = json.decodeFromString<List<WebsiteIndex>>(indexResponse)
                .first { it.defaultAppConfig.isNotBlank() }
            require(index.version == SETTINGS_VERSION) {
                "Expected version '$SETTINGS_VERSION', but got '${index.version}'"
            }
            val configResponse: String = client.get(index.defaultAppConfig).body()
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
