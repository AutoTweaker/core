package io.github.autotweaker.core.data.settings

object CoreConfigRegistry {
    private val _items = mutableMapOf<String, SettingItem>()

    init {
        register("core.agent.tool.response.canceled", SettingItem.Value.ValString("工具调用已取消"))
    }

    private fun register(key: String, default: SettingItem.Value) {
        val item = SettingItem(SettingKey(key), default)
        _items[key] = item
    }

    fun getItem(key: String): SettingItem? = _items[key]
    fun getAllItems(): Collection<SettingItem> = _items.values
}

fun List<SettingItem>.getValue(key: SettingKey): SettingItem.Value {
    return find { it.key == key }?.value
        ?: throw IllegalArgumentException("Setting not found: ${key.value}")
}
