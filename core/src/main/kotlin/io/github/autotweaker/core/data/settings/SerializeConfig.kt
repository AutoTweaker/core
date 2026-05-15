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

package io.github.autotweaker.core.data.settings

import io.github.autotweaker.core.data.HttpFetcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object SerializeConfig {
	private const val CONFIG_VERSION = 1
	
	private val json = Json {
		prettyPrint = false
		ignoreUnknownKeys = true
	}
	
	private var cachedItems: List<SettingItem>? = null
	
	suspend fun fetchDefaultConfig(): List<SettingItem> {
		cachedItems?.let { return it }
		
		val items = fetchFromRemote()
		cachedItems = items
		return items
	}
	
	private suspend fun fetchFromRemote(): List<SettingItem> {
		val index = json.decodeFromString<RootIndex>(HttpFetcher.fetch("index.json"))
		
		val configVersion = index.defaultAppConfig.version.toInt()
		require(configVersion == CONFIG_VERSION) {
			"Config version mismatch  site=$configVersion  local=$CONFIG_VERSION"
		}
		
		val configResponse = HttpFetcher.fetch(index.defaultAppConfig.url)
		return json.decodeFromString(ListSerializer(SettingItem.serializer()), configResponse)
	}
	
	@Serializable
	private data class RootIndex(
		@SerialName("default_app_config") val defaultAppConfig: DefaultAppConfig,
		@SerialName("projects_version") val projectsVersion: ProjectsVersion,
	)
	
	@Serializable
	private data class DefaultAppConfig(
		val url: String,
		val version: String = "",
	)
	
	@Serializable
	private data class ProjectsVersion(
		val core: String,
	)
}
