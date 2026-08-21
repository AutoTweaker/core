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

package io.github.autotweaker.core.infrastructure.persist.json

import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.exception.notfound.ModelNotFoundException
import io.github.autotweaker.api.types.exception.notfound.ProviderNotFoundException
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.types.llm.ModelData.ModelInfo
import io.github.autotweaker.api.types.llm.ProviderData
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persist.json.store.JsonStoreImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.*

class ModelResolverImplTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private val secretMap = mutableMapOf<UUID, String>()
	private val secretStore = object : SecretStore {
		override suspend fun set(secret: String, id: UUID) {
			secretMap[id] = secret
		}
		
		override suspend fun get(id: UUID): String = secretMap[id]!!
		override suspend fun list(): List<UUID> = secretMap.keys.toList()
		override suspend fun remove(id: UUID): Boolean = secretMap.remove(id) != null
		override fun requireUnlocked() {}
	}
	
	private val entries = mutableMapOf<KClass<*>, JsonElement?>()
	
	private fun entryFor(kClass: KClass<*>): JsonStore = mockk<JsonStore>().also {
		every { it.get() } answers { entries[kClass] }
		every { it.set(any()) } answers { entries[kClass] = firstArg<JsonElement>() }
	}
	
	private val modelInfo = ModelInfo(
		modelId = "test-model",
		contextWindow = 128000,
		maxOutputTokens = 4096,
		supportsStreaming = true,
		supportsToolCalls = true,
		supportsReasoning = true,
		supportsImage = false,
		supportsJsonOutput = true,
	)
	
	private fun modelData(id: UUID, providerId: UUID) = ModelData(
		id = id,
		displayName = "model-$id",
		modelInfo = modelInfo,
		providerId = providerId,
	)
	
	private fun providerData(id: UUID): ProviderData {
		val apiKey = UUID.randomUUID()
		secretMap[apiKey] = "sk-$id"
		return ProviderData(
			id = id,
			displayName = "provider-$id",
			providerType = "test",
			apiKey = apiKey,
			baseUrl = "https://api.test.com/v1".toUrl(),
			errorHandlingRules = emptyList(),
		)
	}
	
	@BeforeTest
	fun setUp() {
		entries.clear()
		secretMap.clear()
		mockkObject(JsonStoreImpl)
		every { JsonStoreImpl.namespace(any()) } answers { entryFor(firstArg<KClass<*>>()) }
		ModelResolverImpl.init(secretStore)
		// object 单例的内存状态跨测试残留，逐个清理
		runBlocking {
			ModelStore.getAll().keys.forEach { ModelStore.delete(it) }
			ProviderStore.getAll().keys.forEach { ProviderStore.delete(it) }
		}
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(JsonStoreImpl)
	}
	
	// region ModelStore/ProviderStore CRUD
	
	@Test
	fun `model store set get getAll delete`() = runTest {
		val m1 = modelData(UUID.randomUUID(), UUID.randomUUID())
		val m2 = modelData(UUID.randomUUID(), UUID.randomUUID())
		
		ModelStore.set(m1)
		ModelStore.set(m2)
		
		assertEquals(m1, ModelStore.get(m1.id))
		val expected = mapOf(m1.id to m1, m2.id to m2)
		assertEquals(expected, ModelStore.getAll())
		assertTrue(ModelStore.delete(m1.id))
		assertNull(ModelStore.get(m1.id))
		assertFalse(ModelStore.delete(m1.id))
	}
	
	@Test
	fun `model store overwrites same id`() = runTest {
		val id = UUID.randomUUID()
		ModelStore.set(modelData(id, UUID.randomUUID()))
		
		val expected = modelData(id, UUID.randomUUID())
		ModelStore.set(expected)
		
		assertEquals(expected, ModelStore.get(id))
		assertEquals(1, ModelStore.getAll().size)
	}
	
	@Test
	fun `provider store set get delete`() = runTest {
		val p = providerData(UUID.randomUUID())
		
		ProviderStore.set(p)
		assertEquals(p, ProviderStore.get(p.id))
		assertTrue(ProviderStore.delete(p.id))
		assertNull(ProviderStore.get(p.id))
	}
	
	// endregion
	
	// region resolve 回退链
	
	@Test
	fun `resolve available model returns model with decrypted api key`() = runTest {
		val providerId = UUID.randomUUID()
		val modelId = UUID.randomUUID()
		val provider = providerData(providerId)
		ProviderStore.set(provider)
		ModelStore.set(modelData(modelId, providerId))
		
		val model = ModelResolverImpl.resolve(modelId)
		
		assertNotNull(model)
		assertEquals(modelId, model.id)
		assertEquals(providerId, model.provider.id)
		assertEquals(secretMap[provider.apiKey], model.provider.apiKey)
		assertEquals("test", model.provider.name)
	}
	
	@Test
	fun `resolve missing model falls back to default`() = runTest {
		val providerId = UUID.randomUUID()
		val defaultId = UUID.randomUUID()
		ProviderStore.set(providerData(providerId))
		ModelStore.set(modelData(defaultId, providerId))
		ModelResolverImpl.setDefaultModel(defaultId)
		
		val model = ModelResolverImpl.resolve(UUID.randomUUID())
		
		assertNotNull(model)
		assertEquals(defaultId, model.id)
	}
	
	@Test
	fun `resolve falls back to first available model when default unavailable`() = runTest {
		val brokenProvider = UUID.randomUUID()
		val brokenDefaultId = UUID.randomUUID()
		val goodProvider = UUID.randomUUID()
		val goodModelId = UUID.randomUUID()
		ModelStore.set(modelData(brokenDefaultId, brokenProvider))
		ModelStore.set(modelData(goodModelId, goodProvider))
		ProviderStore.set(providerData(goodProvider))
		ModelResolverImpl.setDefaultModel(brokenDefaultId)
		
		val model = ModelResolverImpl.resolve(UUID.randomUUID())
		
		assertNotNull(model)
		assertEquals(goodModelId, model.id)
	}
	
	@Test
	fun `resolve throws when nothing available`() = runTest {
		assertFailsWith<ModelNotFoundException> {
			ModelResolverImpl.resolve(UUID.randomUUID())
		}
	}
	
	@Test
	fun `resolve throws when provider missing`() = runTest {
		val modelId = UUID.randomUUID()
		ModelStore.set(modelData(modelId, UUID.randomUUID()))
		
		assertFailsWith<ProviderNotFoundException> {
			ModelResolverImpl.resolve(modelId)
		}
	}
	
	// endregion
	
	// region setDefaultModel
	
	@Test
	fun `setDefaultModel persists default id`() = runTest {
		val modelId = UUID.randomUUID()
		ModelStore.set(modelData(modelId, UUID.randomUUID()))
		
		ModelResolverImpl.setDefaultModel(modelId)
		
		assertEquals(modelId, ModelResolverImpl.getDefaultModel())
	}
	
	@Test
	fun `setDefaultModel with missing model fails`() = runTest {
		assertFailsWith<ModelNotFoundException> {
			ModelResolverImpl.setDefaultModel(UUID.randomUUID())
		}
	}
	
	// endregion
}
