package io.github.whiteelephant.autotweaker.core.data.json

import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.agent.llm.Provider

import io.github.whiteelephant.autotweaker.core.data.json.model.AppConfig
import io.github.whiteelephant.autotweaker.core.data.json.store.ConfigFileStore
import kotlin.reflect.KProperty

object DataModule {
    private val store = ConfigFileStore("${System.getProperty("user.home")}/.config/autotweaker/settings.json")

    var current: AppConfig = load()
        private set

    private fun load(): AppConfig = try {
        store.read(AppConfig.serializer()) ?: AppConfig()
    } catch (_: Exception) {
        AppConfig()
    }

    fun update(block: AppConfig.() -> AppConfig) {
        current = current.block()
        store.write(current, AppConfig.serializer())
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AppConfig = current

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: AppConfig) {
        current = value
        store.write(value, AppConfig.serializer())
    }
}
