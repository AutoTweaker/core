package io.github.autotweaker.core.data.settings

object CoreConfigRegistry {
    private val _items = mutableSetOf<SettingItem>()

    init {
        register(
            SettingItem(
                SettingKey("core.agent.tool.response.canceled"),
                SettingItem.Value.ValString("工具调用已取消"),
                "工具调用被取消时的ToolResult"
            )
        )
    }

    private fun register(item: SettingItem) {
        _items.add(item)
    }

    fun getItem(key: String): SettingItem? = _items.find { it.key.value == key }
    fun getAllItems(): Collection<SettingItem> = _items
}

fun List<SettingItem>.getValue(key: SettingKey): SettingItem.Value {
    return find { it.key == key }?.value
        ?: throw IllegalArgumentException("Setting not found: ${key.value}")
}
