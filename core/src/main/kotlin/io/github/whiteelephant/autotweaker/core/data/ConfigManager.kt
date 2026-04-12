package io.github.whiteelephant.autotweaker.core.data

import io.github.whiteelephant.autotweaker.core.data.model.AppConfig
import io.github.whiteelephant.autotweaker.core.data.store.ConfigFileStore

class ConfigManager(private val store: ConfigFileStore) {
    private var _currentConfig: AppConfig = AppConfig()

    val current: AppConfig get() = _currentConfig

    init {
        load()
    }

    fun load() {
        _currentConfig = try {
            store.read(AppConfig.serializer()) ?: AppConfig()
        } catch (e: Exception) {
            AppConfig()
        }
    }

    fun save(newConfig: AppConfig) {
        _currentConfig = newConfig
        store.write(newConfig, AppConfig.serializer())
    }

    fun update(block: AppConfig.() -> AppConfig) {
        save(current.block())
    }
}
