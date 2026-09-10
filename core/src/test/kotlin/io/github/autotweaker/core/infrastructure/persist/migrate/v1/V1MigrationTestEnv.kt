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

package io.github.autotweaker.core.infrastructure.persist.migrate.v1

import io.github.autotweaker.api.CONFIG_PATH
import io.github.autotweaker.api.json
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.core.infrastructure.persist.migrate.MigratorBase
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.*
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config.V0JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config.V0SettingValue
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.config.V0SettingsTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm.V0ContentPart
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm.V0Sha256
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm.V0Url
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.llm.V0Usage
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.*
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.tool.V0ToolResultStatus
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.tool.V0UiBlock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

object V1MigrationTestEnv {
	private val DATABASE_DIR: Path = CONFIG_PATH.resolve("database")
	private val NAMES = listOf("AppConfig", "Sessions", "Traces", "Usages", "Objects")
	
	val V0_SETTINGS: Map<String, V0SettingValue<*>> = mapOf(
		"test.string" to V0SettingValue.ValString("hello"),
		"test.int" to V0SettingValue.ValInt(42),
		"test.boolean" to V0SettingValue.ValBoolean(true),
		"test.byte" to V0SettingValue.ValByte(7),
		"test.short" to V0SettingValue.ValShort(300),
		"test.long" to V0SettingValue.ValLong(9_000_000_000L),
		"test.float" to V0SettingValue.ValFloat(1.5f),
		"test.double" to V0SettingValue.ValDouble(2.25),
		"test.char" to V0SettingValue.ValChar('x'),
		TRACE_MAX_TOTAL_ENTRIES to V0SettingValue.ValInt(500),
	)
	
	fun clean() {
		runBlocking { NAMES.forEach { TestBridge.close(it) } }
		if (Files.isDirectory(DATABASE_DIR)) {
			Files.list(DATABASE_DIR).use { stream ->
				stream.filter { it.fileName.toString().endsWith(".mv.db") }
					.forEach(Files::deleteIfExists)
			}
			listOf("backup", "backup.tmp").forEach { name ->
				val dir = DATABASE_DIR.resolve(name)
				if (Files.isDirectory(dir)) {
					Files.list(dir).use { it.forEach(Files::deleteIfExists) }
					Files.deleteIfExists(dir)
				}
			}
		}
	}
	
	suspend fun <T> inAppConfig(block: suspend JdbcTransaction.() -> T): T =
		TestBridge.run("AppConfig", block)
	
	suspend fun <T> inSessions(block: suspend JdbcTransaction.() -> T): T =
		TestBridge.run("Sessions", block)
	
	suspend fun <T> inDatabase(dbName: String, block: suspend JdbcTransaction.() -> T): T =
		TestBridge.run(dbName, block)
	
	fun buildV0(withSessions: Boolean = true, emptySessions: Boolean = false, bulkMessages: Int = 0): Fixture {
		val fixture = Fixture(bulkMessages)
		runBlocking {
			inAppConfig {
				SchemaUtils.create(V0SettingsTable("settings"), V0JsonStoreTable("json_store"))
				val settings = V0SettingsTable("settings")
				V0_SETTINGS.forEach { (key, value) ->
					settings.insert {
						it[settings.keyName] = key
						it[settings.valJson] = json.encodeToString(V0SettingValue.serializer(), value)
					}
				}
				
				fun workspace(id: UUID, name: String, sessionIds: Set<UUID>) = V0WorkspaceData(
					id = id,
					meta = V0WorkspaceMeta(name, CONFIG_PATH.resolve("workspace")),
					sessionIds = sessionIds,
				)
				
				val store = V0JsonStoreTable("json_store")
				store.insert {
					it[namespace] = WORKSPACE_NAMESPACE
					it[content] = json.encodeToString(
						MapSerializer(UuidSerializer, V0WorkspaceData.serializer()),
						mapOf(
							fixture.workspaceId to workspace(
								fixture.workspaceId,
								"default",
								linkedSetOf(fixture.s1, fixture.s2, fixture.s3),
							),
							fixture.danglingWorkspaceId to workspace(
								fixture.danglingWorkspaceId,
								"dangling",
								linkedSetOf(fixture.s1, fixture.m4, fixture.s3),
							),
							fixture.emptyWorkspaceId to workspace(
								fixture.emptyWorkspaceId,
								"empty",
								linkedSetOf(fixture.m4),
							),
						),
					)
				}
				store.insert {
					it[namespace] = "test.plain"
					it[content] = """{"k":"v"}"""
				}
			}
			if (withSessions) buildSessions(fixture, emptySessions)
			TestBridge.close("AppConfig")
			TestBridge.close("Sessions")
		}
		return fixture
	}
	
	private fun buildSessions(fixture: Fixture, empty: Boolean) {
		runBlocking {
			inSessions {
				SchemaUtils.create(
					V0SessionTable("session_data"),
					V0AgentTable("agent_data"),
					V0MessageTable("session_message"),
				)
				if (empty) return@inSessions
				val sessions = V0SessionTable("session_data")
				fun session(id: UUID, agentId: UUID) {
					sessions.insert {
						it[sessions.id] = id
						it[title] = "session-$id"
						it[overview] = null
						it[workspaceId] = fixture.workspaceId
						it[agentIndexJson] = json.encodeToString(
							V0AgentIndex.serializer(),
							V0AgentIndex(V0AgentIndex.AgentNode(agentId, emptyList())),
						)
					}
				}
				session(fixture.s1, fixture.a1)
				session(fixture.s2, fixture.a2)
				session(fixture.s3, fixture.a3)
				if (fixture.bulkIds.isNotEmpty()) session(fixture.bulkSession, fixture.bulkAgent)
				
				val agents = V0AgentTable("agent_data")
				fun agent(id: UUID, messageIds: List<UUID>, dropped: Set<UUID> = emptySet()) {
					val index = if (messageIds.isEmpty()) {
						V0AgentContextIndex(compactedRounds = null, historyRounds = null, currentRound = null)
					} else {
						V0AgentContextIndex(
							compactedRounds = null,
							historyRounds = null,
							currentRound = V0AgentContextIndex.CurrentRound(
								userMessage = messageIds.first(),
								turns = null,
								assistantMessage = messageIds.getOrNull(1),
								finishedToolCalls = null,
								pendingToolCalls = null,
							),
						)
					}
					agents.insert {
						it[agents.id] = id
						it[name] = "main"
						it[modelJson] = json.encodeToString(
							V0ModelConfig.serializer(),
							V0ModelConfig(
								model = fixture.modelId,
								reasoning = null,
								summarize = fixture.modelId,
								compact = fixture.modelId,
								fallback = emptyList(),
							),
						)
						it[contextJson] = json.encodeToString(
							V0AgentContext.serializer(),
							V0AgentContext(
								systemPrompt = "system",
								injections = null,
								index = index,
								droppedMessages = dropped.ifEmpty { null },
							),
						)
						it[activeToolsJson] = "[]"
					}
				}
				agent(
					fixture.a1,
					listOf(fixture.m1, fixture.m2),
					setOf(fixture.m5, fixture.tc1, fixture.tr1, fixture.cp1, fixture.ur1),
				)
				agent(fixture.a2, emptyList())
				agent(fixture.a3, listOf(fixture.m3))
				if (fixture.bulkIds.isNotEmpty()) agent(fixture.bulkAgent, emptyList(), fixture.bulkIds.toSet())
				
				val messages = V0MessageTable("session_message")
				fun message(content: V0AgentMessage) {
					messages.insert {
						it[messages.id] = content.id
						it[messages.contentJson] = json.encodeToString(V0AgentMessage.serializer(), content)
					}
				}
				message(
					V0AgentMessage.User(
						id = fixture.m1,
						timestamp = fixture.t1,
						content = V0MessageContent(
							injections = null,
							content = listOf(V0ContentPart.Text(MARKER)),
						),
					)
				)
				message(
					V0AgentMessage.Assistant(
						id = fixture.m2,
						timestamp = fixture.t2,
						reasoning = null,
						content = "reply",
						model = fixture.modelId,
						usage = null,
					)
				)
				message(
					V0AgentMessage.User(
						id = fixture.m3,
						timestamp = fixture.t3,
						content = V0MessageContent(injections = null, content = listOf(V0ContentPart.Text("third"))),
					)
				)
				message(
					V0AgentMessage.User(
						id = fixture.m4,
						timestamp = fixture.t1,
						content = V0MessageContent(injections = null, content = listOf(V0ContentPart.Text("orphan"))),
					)
				)
				message(
					V0AgentMessage.User(
						id = fixture.m5,
						timestamp = fixture.t1 + 1.hours,
						content = V0MessageContent(
							injections = listOf(V0ContextInjection(tag = "ctx", content = "injected")),
							content = listOf(
								V0ContentPart.Text("mixed"),
								V0ContentPart.Image("image/png", V0Sha256("a".repeat(64))),
								V0ContentPart.ImageUrl(V0Url("https://example.com/a.png")),
							),
						),
					)
				)
				message(
					V0AgentMessage.Tool.Call(
						id = fixture.tc1,
						timestamp = fixture.t1 + 2.hours,
						callId = "toolu_1",
						callName = "bash",
						arguments = """{"command":"toolcallmarker"}""",
						reason = "inspect files",
						validatedToolName = "bash",
						validatedArgs = json.parseToJsonElement("""{"command":"toolcallmarker"}"""),
						resolvedRequest = null,
						presentation = listOf(V0UiBlock.Command("ls")),
					)
				)
				message(
					V0AgentMessage.Tool.Result(
						id = fixture.tr1,
						timestamp = fixture.t1 + 3.hours,
						callId = "toolu_1",
						content = "toolresultmarker",
						data = json.parseToJsonElement("""{"name":"file.txt"}"""),
						presentation = listOf(V0UiBlock.Output("ok")),
						status = V0ToolResultStatus.SUCCESS,
					)
				)
				message(
					V0AgentMessage.Compact(
						id = fixture.cp1,
						timestamp = fixture.t1 + 4.hours,
						content = "compactsummarymarker",
						model = fixture.modelId,
						usage = V0Usage(
							promptTokens = 11,
							completionTokens = 22,
							reasoningTokens = 3,
							cacheHitTokens = 4
						),
					)
				)
				message(
					V0AgentMessage.UsageRecord(
						id = fixture.ur1,
						timestamp = fixture.t1 + 5.hours,
						model = fixture.modelId,
						usage = V0Usage(promptTokens = 5, completionTokens = 6),
					)
				)
				fixture.bulkIds.forEachIndexed { index, id ->
					message(
						V0AgentMessage.User(
							id = id,
							timestamp = fixture.bulkStart + index.seconds,
							content = V0MessageContent(
								injections = null,
								content = listOf(V0ContentPart.Text("bulk $index")),
							),
						)
					)
				}
			}
			TestBridge.close("Sessions")
		}
	}
	
	const val WORKSPACE_NAMESPACE = "io.github.autotweaker.core.infrastructure.persist.json.WorkspaceManager"
	const val MARKER = "uniquemarkerzqx"
	const val TRACE_MAX_TOTAL_ENTRIES =
		"io.github.autotweaker.core.infrastructure.persist.db.trace.TraceSettings.MaxTotalEntries"
	
	class Fixture(bulkCount: Int = 0) {
		val workspaceId: UUID = UUID.randomUUID()
		val danglingWorkspaceId: UUID = UUID.randomUUID()
		val emptyWorkspaceId: UUID = UUID.randomUUID()
		val s1: UUID = UUID.randomUUID()
		val s2: UUID = UUID.randomUUID()
		val s3: UUID = UUID.randomUUID()
		val bulkSession: UUID = UUID.randomUUID()
		val a1: UUID = UUID.randomUUID()
		val a2: UUID = UUID.randomUUID()
		val a3: UUID = UUID.randomUUID()
		val bulkAgent: UUID = UUID.randomUUID()
		val m1: UUID = UUID.randomUUID()
		val m2: UUID = UUID.randomUUID()
		val m3: UUID = UUID.randomUUID()
		val m4: UUID = UUID.randomUUID()
		val m5: UUID = UUID.randomUUID()
		val tc1: UUID = UUID.randomUUID()
		val tr1: UUID = UUID.randomUUID()
		val cp1: UUID = UUID.randomUUID()
		val ur1: UUID = UUID.randomUUID()
		val bulkIds: List<UUID> = List(bulkCount) { UUID.randomUUID() }
		val modelId: UUID = UUID.randomUUID()
		val t1: Instant = Instant.parse("2024-01-01T00:00:00Z")
		val t2: Instant = Instant.parse("2024-01-02T00:00:00Z")
		val t3: Instant = Instant.parse("2024-03-03T00:00:00Z")
		val bulkStart: Instant = Instant.parse("2024-03-03T00:00:01Z")
	}
	
	private object TestBridge : MigratorBase() {
		suspend fun <T> run(dbName: String, block: suspend JdbcTransaction.() -> T): T =
			transaction(dbName, block)
		
		suspend fun close(dbName: String) = shutdown(dbName)
	}
}
