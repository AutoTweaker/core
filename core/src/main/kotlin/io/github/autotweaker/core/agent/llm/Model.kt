package io.github.autotweaker.core.agent.llm

import io.github.autotweaker.core.Url
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.ErrorHandlingRule
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.Model.Config
import io.github.autotweaker.core.data.settings.SettingItem.Value.Providers.Provider.Model.ModelInfo

data class Model(
	val name: String,
	val provider: Provider,
	val modelInfo: ModelInfo,
	val config: Config? = null,
)

data class Provider(
	val name: String,
	val baseUrl: Url,
	val apiKey: String,
	val errorHandlingRules: List<ErrorHandlingRule>
)
