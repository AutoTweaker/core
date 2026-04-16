package io.github.whiteelephant.autotweaker.core.data.database.settings

object CoreConfigRegistry {
    private val _items = mutableMapOf<String, SettingItem<*>>()

    init {
        register("core.agent.tool.response.canceled", "工具调用已取消")
    }

    private fun <T : Any> register(key: String, default: T) {
        val item = SettingItem(SettingKey(key), default)
        _items[key] = item
    }

    fun getItem(key: String): SettingItem<*>? = _items[key]
    fun getAllItems(): Collection<SettingItem<*>> = _items.values
}
