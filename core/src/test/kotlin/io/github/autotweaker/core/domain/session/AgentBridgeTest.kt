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

import io.github.autotweaker.api.store.JsonStore
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.llm.ChatMessage
import io.github.autotweaker.api.types.llm.ChatResult
import io.github.autotweaker.api.types.llm.LlmResult
import io.github.autotweaker.api.types.llm.toContentPart
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.agent.RuntimeModel
import io.github.autotweaker.core.domain.agent.chat.merge
import io.github.autotweaker.core.domain.chat.ResilientChat
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.domain.port.UsageRepository
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreImpl
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AgentBridgeTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	private lateinit var dir: Path
	private lateinit var store: SessionRepository
	
	private fun modelConfig() = ModelConfig(
		model = UUID.randomUUID(),
		reasoning = null,
		summarize = UUID.randomUUID(),
		compact = UUID.randomUUID(),
		fallback = emptyList(),
	)
	
	private fun agentData(id: UUID = UUID.randomUUID()) = AgentData(
		id = id,
		name = "main-agent".toKebab(),
		model = modelConfig(),
		context = AgentContext.emptyContext("system prompt"),
		activeTools = emptySet(),
	)
	
	private suspend fun bridge(data: AgentData = agentData()): AgentBridge = AgentBridge(
		host = mockk(),
		sessionRepo = store,
		usageRepo = mockk<UsageRepository>(relaxed = true),
		resolveModel = {
			// toModelConfig() 需要非空 Model.id
			mockk<RuntimeModel>(relaxed = true).also { model -> every { model.id } returns UUID.randomUUID() }
		},
		workspace = dir,
	).init(data)
	
	private suspend fun awaitUntil(condition: () -> Boolean) {
		// Agent 的 workLoop 跑在真实调度器上，轮询必须使用真实时间而非 runTest 的虚拟时间
		withContext(Dispatchers.Default.limitedParallelism(1)) {
			withTimeout(5.seconds) {
				while (!condition()) delay(10.milliseconds)
			}
		}
	}
	
	@BeforeTest
	fun setUp() {
		dir = Files.createTempDirectory("bridge-test")
		store = mockk<SessionRepository>(relaxed = true)
		// 工具 meta() 会访问 JsonStore（EnvStore 等），屏蔽底层 H2
		mockkObject(JsonStoreImpl)
		every { JsonStoreImpl.namespace(any()) } answers {
			mockk<JsonStore>().also {
				every { it.get() } answers { null }
				every { it.set(any()) } answers { }
			}
		}
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(JsonStoreImpl)
		dir.toFile().deleteRecursively()
	}
	
	@Test
	fun `init builds agent and loads tools`() = runTest {
		val data = agentData()
		val b = bridge(data)
		
		assertEquals(data.id, b.agent.agentId)
		assertTrue(b.agent.activeTools.value.isEmpty())
		assertNull(b.agent.context.value.currentRound)
		
		b.shutdown()
	}
	
	@Test
	fun `inject adds context injection to agent`() = runTest {
		val b = bridge()
		val injection = ContextInjection(tag = "ctx", content = "data")
		
		b.inject(injection)
		
		awaitUntil { b.agent.context.value.injections?.contains(injection) == true }
		b.shutdown()
	}
	
	@Test
	fun `remove injection removes from agent context after sync`() = runTest {
		// removeInjection 读取的是 _context（AgentContext 快照），需等异步 save 链同步
		val saveCount = java.util.concurrent.atomic.AtomicInteger(0)
		coEvery { store.saveAgent(any()) } answers {
			Unit.also { saveCount.incrementAndGet() }
		}
		val b = bridge()
		val injection = ContextInjection(tag = "ctx", content = "data")
		b.inject(injection)
		awaitUntil { b.agent.context.value.injections?.contains(injection) == true }
		awaitUntil { saveCount.get() >= 1 }
		
		b.removeInjection(injection.id)
		
		awaitUntil { b.agent.context.value.injections?.any { it.id == injection.id } != true }
		b.shutdown()
	}
	
	@Test
	fun `send message drives round and persists messages`() = runTest {
		mockkObject(ResilientChat)
		coEvery {
			ResilientChat.execute(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
		} returns flow {
			emit(
				LlmResult(
					ChatResult.Assembled(
						message = ChatMessage.Assistant("answer", Clock.System.now()),
					),
					model = UUID.randomUUID(),
				)
			)
		}
		val b = bridge()
		
		b.send(MessageContent(content = "hello".toContentPart()))
		awaitUntil { b.agent.context.value.historyRounds?.size == 1 }
		
		val completed = b.agent.context.value.historyRounds!!.single()
		assertEquals("hello\n", completed.userMessage.content.content?.merge())
		assertEquals("answer", completed.finalAssistantMessage?.content)
		coVerify(atLeast = 1) { store.saveMessages(any()) }
		
		b.shutdown()
		unmockkObject(ResilientChat)
	}
	
	@Test
	fun `shutdown saves final context`() = runTest {
		val b = bridge()
		
		b.shutdown()
		
		coVerify(atLeast = 1) { store.saveAgent(any()) }
	}
}
