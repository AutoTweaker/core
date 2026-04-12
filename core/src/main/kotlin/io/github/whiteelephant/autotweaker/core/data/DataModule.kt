package io.github.whiteelephant.autotweaker.core.data

import io.github.whiteelephant.autotweaker.core.Url
import io.github.whiteelephant.autotweaker.core.agent.llm.Model
import io.github.whiteelephant.autotweaker.core.agent.llm.Provider

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

    /**
     * Resolves a "providerName/modelName" spec string into a [Model].
     * @throws IllegalArgumentException if the format is invalid or no matching provider/model is found.
     */
    fun resolveModel(spec: String): Model {
        val slashIndex = spec.indexOf('/')
        require(slashIndex > 0 && slashIndex < spec.length - 1) {
            "Invalid model spec \"$spec\", expected format: \"provider/model\""
        }
        val providerName = spec.substring(0, slashIndex)
        val modelName = spec.substring(slashIndex + 1)

        val provider = current.providers.find { it.name == providerName }
            ?: throw IllegalArgumentException(
                "Provider \"$providerName\" not found, available: ${current.providers.joinToString { it.name }}"
            )
        val model = provider.models.find { it.name == modelName }
            ?: throw IllegalArgumentException(
                "Model \"$modelName\" not found in provider \"$providerName\", available: ${provider.models.joinToString { it.name }}"
            )

        return Model(
            name = model.name,
            provider = Provider(
                name = provider.name,
                baseUrl = Url(provider.baseUrl),
                apiKey = provider.apiKey,
                errorHandlingRules = provider.errorHandlingRules,
            ),
            contextWindow = model.contextWindow,
            maxOutputTokens = model.maxOutputTokens,
            price = model.price,
            supportsStreaming = model.supportsStreaming,
            supportsToolCalls = model.supportsToolCalls,
            supportsReasoning = model.supportsReasoning,
            supportsImage = model.supportsImage,
        )
    }
}
