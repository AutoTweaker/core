import io.github.autotweaker.core.data.settings.CoreConfigRegistry
import io.github.autotweaker.core.data.settings.SettingItem
import kotlinx.serialization.json.*
import java.io.File

fun main() {
    val items = CoreConfigRegistry.getAllItems()
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    val jsonArray = JsonArray(items.map { item ->
        buildJsonObject {
            put("key", item.key.value)
            put("value", json.encodeToJsonElement(SettingItem.Value.serializer(), item.value))
            put("description", item.description)
        }
    })

    val outputFile = File("AppConfig.json")
    outputFile.parentFile.mkdirs()
    outputFile.writeText(json.encodeToString(JsonArray.serializer(), jsonArray))

    println("Config serialized to ${outputFile.absolutePath}")
    println("Total items: ${items.size}")
}
