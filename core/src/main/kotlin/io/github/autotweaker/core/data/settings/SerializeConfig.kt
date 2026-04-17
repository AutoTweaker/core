package io.github.autotweaker.core.data.settings

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object SerializeConfig {
    private const val INDEX_URL = "https://autotweaker.github.io/website/"

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private var cachedItems: List<SettingItem>? = null

    /**
     * 配置项定义（配置的唯一来源）
     */
    val defaultItems: List<SettingItem> = listOf(
        SettingItem(
            SettingKey("core.agent.tool.response.canceled"),
            SettingItem.Value.ValString("工具调用已取消"),
            "工具调用被取消时的ToolResult"
        ),
    )

    suspend fun fetchDefaultConfig(): List<SettingItem> {
        cachedItems?.let { return it }

        HttpClient(Java).use { client ->
            val indexResponse: String = client.get(INDEX_URL).body()
            val index = json.decodeFromString<List<WebsiteIndex>>(indexResponse).first()
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

    fun serializeToFile(items: List<SettingItem>, outputPath: String) {
        val content = json.encodeToString(ListSerializer(SettingItem.serializer()), items)
        val outputFile = File(outputPath)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(content)
        println("Config serialized to ${outputFile.absolutePath}")
        println("Total items: ${items.size}")
    }
}

fun main(args: Array<String>) {
    val outputPath = args.firstOrNull()
        ?: throw IllegalArgumentException("Output path is required")
    SerializeConfig.serializeToFile(SerializeConfig.defaultItems, outputPath)
}
