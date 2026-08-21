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

@file:Suppress("UnusedReceiverParameter")

package io.github.autotweaker.adapter.cli.commands.session.model

import com.google.auto.service.AutoService
import io.github.autotweaker.adapter.cli.commands.Console
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.base.I18nBase
import io.github.autotweaker.api.base.store.ImmutableStore
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.discard
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.agent.ModelConfig
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

object ModelManager : ImmutableStore<ModelManager.SessionModelConfig>() {
	override val serializer = SessionModelConfig.serializer()
	override fun default() = SessionModelConfig()
	
	private lateinit var core: CoreAPI
	
	fun init(c: CoreAPI) {
		core = c
	}
	
	suspend fun Console.setMain(id: UUID) =
		cache.update {
			it.copy(
				model = id
			)
		}.discard()
	
	suspend fun Console.setThinking(thinking: Boolean) =
		cache.update {
			it.copy(
				thinking = thinking
			)
		}.discard()
	
	suspend fun Console.setSummarize(id: UUID) =
		cache.update {
			it.copy(
				summarize = id
			)
		}.discard()
	
	suspend fun Console.setCompact(id: UUID) =
		cache.update {
			it.copy(
				compact = id
			)
		}.discard()
	
	suspend fun Console.addFallback(id: UUID) =
		cache.update {
			it.copy(
				fallback = it.fallback + id
			)
		}.discard()
	
	suspend fun Console.removeFallback(id: UUID) =
		cache.update {
			it.copy(
				fallback = it.fallback - id
			)
		}.discard()
	
	suspend fun Console.removeInvalid() =
		cache.update { old ->
			val modelIds = core.config.listModels().map { it.id }.toSet()
			fun UUID?.ifValid() = this?.takeIf { it in modelIds }
			SessionModelConfig(
				model = old.model.ifValid(),
				thinking = old.thinking,
				summarize = old.summarize.ifValid(),
				compact = old.compact.ifValid(),
				fallback = old.fallback intersect modelIds
			)
		}.discard()
	
	
	suspend fun Console.getConfig(): ModelConfig = cache.get().let {
		suspend fun UUID?.orDefault() =
			this ?: it.model
			?: core.config.getDefaultModel()
			?: error(ModelNotSet())
		ModelConfig(
			model = it.model.orDefault(),
			thinking = it.thinking,
			summarize = it.summarize.orDefault(),
			compact = it.compact.orDefault(),
			fallback = it.fallback.toList()
		)
	}
	
	@Serializable
	data class SessionModelConfig(
		@Serializable(with = UuidSerializer::class)
		val model: UUID? = null,
		val thinking: Boolean = true,
		@Serializable(with = UuidSerializer::class)
		val summarize: UUID? = null,
		@Serializable(with = UuidSerializer::class)
		val compact: UUID? = null,
		val fallback: Set<@Serializable(with = UuidSerializer::class) UUID> = emptySet(),
	)
	
	@AutoService(I18nDef::class)
	class ModelNotSet : I18nBase(
		zh("主模型未设置，请先设置模型"),
	)
}
