/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.core.adapter.config

import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.provider.ProviderData
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.ModelId
import io.github.autotweaker.api.types.settings.SettingKey
import io.github.autotweaker.core.container.ContainerManager
import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.data.provider.ProviderStore
import io.github.autotweaker.core.data.settings.Settings
import io.github.autotweaker.core.llm.LlmClientLoader
import io.github.autotweaker.core.secret.SecretManager
import io.github.autotweaker.core.session.ProviderService
import io.github.autotweaker.core.tool.impl.bash.Bash
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.*

object ConfigManager {
	private val secret = SecretManager
	
	object AppConfigAPI {
		private val cfg = Settings
		
		fun get(id: SettingKey): CoreConfig.AppConfig? =
			cfg.get().find { it.key == id }?.let { CoreConfig.AppConfig(it) }
		
		fun set(setting: CoreConfig.AppConfig) = cfg.set(setting.setting)
		fun getAll(): List<CoreConfig.AppConfig> = cfg.get().map { CoreConfig.AppConfig(it) }
	}
	
	object EnvConfigAPI {
		private val bash = Bash()
		private val con = ContainerManager
		
		fun list(type: CoreConfig.JsonConfig.Env.Type): List<String> =
			if (type == CoreConfig.JsonConfig.Env.Type.BASH_ENV) {
				bash.listEnv()
			} else {
				con.listEnv()
			}
		
		fun set(env: List<CoreConfig.JsonConfig.Env>) {
			val bashEnv = env.filter { it.type == CoreConfig.JsonConfig.Env.Type.BASH_ENV }
			val conEnv = env.filter { it.type == CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV }
			bashEnv.forEach { bash.setEnv(it.id, it.value) }
			con.setEnv(conEnv.associateBy({ it.id }, { it.value }))
		}
		
		fun get(type: CoreConfig.JsonConfig.Env.Type, id: String): String? =
			if (type == CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV) {
				con.getEnv(id)[id]
			} else {
				bash.getEnv(id)
			}
		
		fun remove(type: CoreConfig.JsonConfig.Env.Type, id: String) =
			if (type == CoreConfig.JsonConfig.Env.Type.CONTAINER_ENV) {
				con.removeEnv(id)
			} else {
				bash.removeEnv(id)
			}
	}
	
	object ProviderConfigAPI {
		private val store = ProviderStore
		
		object ProviderAPI {
			fun listAvailable(): List<String> = LlmClientLoader.availableProviders()
			fun getMeta(type: String) = ProviderService.getInfo(type)
			fun list() = store.get().map {
				CoreConfig.ProviderConfig.Provider(
					name = it.name,
					type = it.providerType,
					keyId = ApiKeyAPI.getName(it.apiKey),
					baseUrl = it.baseUrl,
					errorHandlingRules = it.errorHandlingRules,
				)
			}
			
			fun delete(name: String) = store.delete(name)
			
			fun create(provider: CoreConfig.ProviderConfig.Provider) {
				val meta = ProviderService.getInfo(provider.type)
				store.add(
					ProviderData(
						name = provider.name,
						providerType = provider.type,
						apiKey = ApiKeyAPI.getId(provider.keyId),
						baseUrl = provider.baseUrl ?: meta.baseUrl,
						models = emptyList(),
						errorHandlingRules = provider.errorHandlingRules ?: meta.errorHandlingRules,
					)
				)
			}
			
			fun updateType(name: String, new: String) = store.override(get(name).copy(providerType = new))
			
			fun updateKey(name: String, keyName: String) =
				store.override(get(name).copy(apiKey = ApiKeyAPI.getId(keyName)))
			
			fun updateUrl(name: String, url: Url) = store.override(get(name).copy(baseUrl = url))
			
			fun updateRule(name: String, rules: List<ProviderData.ErrorHandlingRule>) =
				store.override(get(name).copy(errorHandlingRules = rules))
			
			fun rename(name: String, new: String) {
				val old = get(name)
				store.delete(name)
				store.add(old.copy(name = new))
			}
			
			internal fun get(name: String) =
				store.get().find { it.name == name } ?: error("ProviderData $name not found")
		}
		
		object ModelAPI {
			fun getMeta(provider: String, modelId: String) =
				ProviderAPI.getMeta(provider).models.find { it.id == modelId }
			
			fun add(model: CoreConfig.ProviderConfig.Model) {
				val modelInfo = model.meta ?: getMeta(model.providerName, model.name) ?: error("Default meta not found")
				val newModel = ProviderData.ModelData(
					name = model.name,
					modelInfo = modelInfo,
					config = model.config,
				)
				store.addModel(model.providerName, listOf(newModel))
			}
			
			fun list() = store.get().flatMap {
				it.models.map { model ->
					CoreConfig.ProviderConfig.Model(
						name = model.name,
						providerName = it.name,
						meta = model.modelInfo,
						config = model.config,
					)
				}
			}
			
			fun listId() = list().map { ModelId(provider = it.providerName, modelName = it.name) }
			
			fun remove(id: ModelId) {
				val provider = ProviderAPI.get(id.provider)
				val updatedModels = provider.models.filterNot { it.name == id.modelName }
				store.override(provider.copy(models = updatedModels))
			}
			
			fun update(id: ModelId, model: CoreConfig.ProviderConfig.Model) {
				val provider = ProviderAPI.get(id.provider)
				val modelInfo = model.meta ?: getMeta(model.providerName, model.name) ?: error("Default meta not found")
				val newModel = ProviderData.ModelData(
					name = model.name,
					modelInfo = modelInfo,
					config = model.config,
				)
				val updatedModels = provider.models.map {
					if (it.name == id.modelName) newModel else it
				}
				store.override(provider.copy(models = updatedModels))
			}
		}
		
		object ApiKeyAPI {
			private val jsonEntry = JsonStore.namespace(this::class.java.name)
			private val keyMap: MutableMap<String, @Serializable(with = UuidSerializer::class) UUID> = mutableMapOf()
			
			fun add(key: CoreConfig.ProviderConfig.ApiKey) {
				if (keyMap[key.name] != null) error("Key ${key.name} already exists")
				keyMap[key.name] = secret.add(key.key)
				saveMap()
			}
			
			fun list(): List<String> = keyMap.keys.toList()
			fun get(name: String): String = keyMap[name]?.let { secret.get(it) } ?: error("Key $name not found")
			fun delete(name: String) {
				val id = keyMap.remove(name) ?: error("Key $name not found")
				secret.remove(id)
				saveMap()
			}
			
			internal fun getId(name: String): UUID = keyMap[name] ?: error("Key $name not found")
			internal fun getName(id: UUID): String =
				keyMap.filter { it.value == id }.keys.firstOrNull() ?: error("Key $id not found")
			
			init {
				jsonEntry.get()?.let {
					keyMap.putAll(
						Json.decodeFromJsonElement(MapSerializer(String.serializer(), UuidSerializer), it)
					)
				}
			}
			
			private fun saveMap() =
				jsonEntry.set(Json.encodeToJsonElement(MapSerializer(String.serializer(), UuidSerializer), keyMap))
		}
	}
}
