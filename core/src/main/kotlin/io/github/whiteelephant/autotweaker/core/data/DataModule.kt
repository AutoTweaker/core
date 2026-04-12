package io.github.whiteelephant.autotweaker.core.data

import io.github.whiteelephant.autotweaker.core.data.model.AppConfig
import io.github.whiteelephant.autotweaker.core.data.store.ConfigFileStore
import kotlin.reflect.KProperty

object DataModule {
    private val store = ConfigFileStore("${System.getProperty("user.home")}/.config/autotweaker/settings.json")

    private val configManager = ConfigManager(store)

    val current: AppConfig get() = configManager.current

    fun update(block: AppConfig.() -> AppConfig) = configManager.update(block)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AppConfig = current

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: AppConfig) {
        configManager.save(value)
    }
}
