package io.github.whiteelephant.autotweaker.core.data.database

object CoreConfigRegistry {
    private val _items = mutableMapOf<String, SettingItem<*>>()

    init {
        // 注册全部配置项
        register("core.tool.read", "example")
    }

    private fun <T : Any> register(key: String, default: T) {
        val item = SettingItem(SettingKey(key), default)
        _items[key] = item
    }

    fun getItem(key: String): SettingItem<*>? = _items[key]
    fun getAllItems(): Collection<SettingItem<*>> = _items.values
}
