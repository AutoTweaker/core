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

package io.github.autotweaker.core.domain.session

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.ReentrantMutex
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.store.JsonStoreAccessor
import io.github.autotweaker.api.types.agent.AgentMessage
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.llm.UsageSnapshot
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.time.Duration.Companion.seconds

object UsageStore : JsonStorable, Loggable, Traceable {
	private val lock = ReentrantMutex()
	private val scope = scope(IO)
	
	@Volatile
	private var dirty = false
	
	@Volatile
	private var initialized = false
	
	private val accessor by lazy {
		JsonStoreAccessor(store, Data.serializer()) { Data() }
	}
	private val records by lazy {
		accessor.initial.records.toMutableMap()
			.also { initialized = true }
	}
	private val models by lazy {
		accessor.initial.models.toMutableList()
			.also { initialized = true }
	}
	
	init {
		scope.launch {
			var retry = 0
			while (true) {
				delay(3.seconds)
				if (retry >= 5) delay(60.seconds)
				if (dirty) {
					val data: Data
					lock.withLock {
						data = Data(records.toMap(), models.toList())
						dirty = false
					}
					trace.catching {
						accessor.save(data)
						retry = 0
					}.rethrowCancellation().onFailure {
						dirty = true
						retry++
						log.error("Failed records save", it)
					}
				}
			}
		}
	}
	
	suspend fun shutdown() {
		scope.coroutineContext.job.cancelAndJoin()
		if (initialized) lock.withLock { accessor.save(Data(records, models)) }
	}
	
	suspend fun collect(messages: List<AgentMessage>) =
		messages.forEach { message ->
			when (message) {
				is AgentMessage.Assistant -> message.usageSnapshot?.let { snapshot -> addRecord(message.id, snapshot) }
				is AgentMessage.Compact -> message.snapshots?.forEach { (id, snapshot) -> addRecord(id, snapshot) }
				is AgentMessage.UsageRecord -> addRecord(message.id, message.snapshot)
				else -> {}
			}
		}.andLog(log) {
			info("Collected usage entries  total={}", records.size)
		}
	
	suspend fun getAll() = lock.withLock {
		records.mapValues { it.value.usage }
	}
	
	suspend fun getSnapshot(id: UUID): UsageSnapshot? = lock.withLock {
		records[id]?.let { UsageSnapshot(it.usage, models[it.model]) }
	}
	
	suspend fun modelOf(id: UUID): ModelData.ModelInfo? = lock.withLock {
		records[id]?.let { models[it.model] }
	}
	
	private suspend fun addRecord(id: UUID, snapshot: UsageSnapshot) = lock.withLock {
		if (records.containsKey(id)) return@withLock
		val model = models.indexOf(snapshot.model).let {
			if (it >= 0) it else models.size.also { models.add(snapshot.model) }
		}
		records[id] = Record(
			snapshot.usage, model
		)
		dirty = true
	}
	
	@Serializable
	private data class Data(
		val records: Map<@Serializable(with = UuidSerializer::class) UUID, Record> = emptyMap(),
		val models: List<ModelData.ModelInfo> = emptyList(),
	)
	
	@Serializable
	private data class Record(
		val usage: Usage,
		val model: Int,
	)
}
