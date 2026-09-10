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
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.api.types.agent.*
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.llm.Usage
import io.github.autotweaker.api.types.serializer.UuidSerializer
import io.github.autotweaker.api.types.session.WorkspaceData
import io.github.autotweaker.api.types.tool.ToolResultStatus
import io.github.autotweaker.api.types.tool.UiBlock
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import io.github.autotweaker.core.infrastructure.persist.db.json.JsonStoreTable
import io.github.autotweaker.core.infrastructure.persist.db.session.MessageSearch
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentContext
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentContextIndex
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0AgentIndex
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.agent.V0ModelConfig
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0AgentTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v0.session.V0SessionTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.config.V1SettingsTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1AgentDataTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1AgentMessageTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1MessageOwnershipTable
import io.github.autotweaker.core.infrastructure.persist.migrate.model.v1.session.V1SessionDataTable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.nio.file.Files
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class V1DataMigrationTest {
	private val settingColumns: List<(ResultRow) -> Any?> = listOf(
		{ it[V1SettingsTable.byteValue] },
		{ it[V1SettingsTable.shortValue] },
		{ it[V1SettingsTable.intValue] },
		{ it[V1SettingsTable.longValue] },
		{ it[V1SettingsTable.floatValue] },
		{ it[V1SettingsTable.doubleValue] },
		{ it[V1SettingsTable.booleanValue] },
		{ it[V1SettingsTable.charValue] },
		{ it[V1SettingsTable.stringValue] },
	)
	
	@BeforeTest
	fun cleanUp() {
		V1MigrationTestEnv.clean()
	}
	
	@Test
	fun `settings are migrated into typed columns`() = runBlocking {
		V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		assertEquals(V1MigrationTestEnv.V0_SETTINGS.keys, settingKeys())
		
		assertEquals("hello", settingValue("test.string"))
		assertEquals(42, settingValue("test.int"))
		assertEquals(true, settingValue("test.boolean"))
		assertEquals(7.toByte(), settingValue("test.byte"))
		assertEquals(300.toShort(), settingValue("test.short"))
		assertEquals(9_000_000_000L, settingValue("test.long"))
		assertEquals(1.5f, settingValue("test.float"))
		assertEquals(2.25, settingValue("test.double"))
		assertEquals("x", settingValue("test.char"))
		assertEquals(500L, settingValue(TRACE_MAX_TOTAL_ENTRIES))
		
		val tables = tablesOf("AppConfig")
		assertFalse(tables.contains("settings_v0"))
		assertFalse(tables.contains("json_store_v0"))
		assertTrue(
			constraintNames("AppConfig", "settings").any { it.contains("single_value", ignoreCase = true) },
			"settings is missing the single_value check constraint",
		)
	}
	
	@Test
	fun `json store documents are migrated`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val plain = json.decodeFromJsonElement<JsonObject>(readJson("test.plain"))
		assertEquals("v", plain.getValue("k").jsonPrimitive.content)
		
		val workspace = readWorkspaces().getValue(fixture.workspaceId)
		assertEquals(CONFIG_PATH.resolve("workspace"), workspace.path)
		assertEquals("default", workspace.displayName)
		assertEquals(setOf(fixture.s1, fixture.s2, fixture.s3), workspace.sessionIds)
		assertEquals(fixture.t1, workspace.creationTime)
		assertEquals(fixture.t3, workspace.lastAccessTime)
	}
	
	@Test
	fun `workspaces drop dangling sessions and fall back when empty`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val workspaces = readWorkspaces()
		val dangling = workspaces.getValue(fixture.danglingWorkspaceId)
		assertEquals(setOf(fixture.s1, fixture.s3), dangling.sessionIds)
		assertEquals(fixture.t1, dangling.creationTime)
		assertEquals(fixture.t3, dangling.lastAccessTime)
		
		val empty = workspaces.getValue(fixture.emptyWorkspaceId)
		assertTrue(empty.sessionIds.isEmpty())
		assertTrue(empty.creationTime > fixture.t3)
		assertTrue(empty.lastAccessTime >= empty.creationTime)
	}
	
	@Test
	fun `messages are migrated with origin and type`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val messages = readMessages()
		assertEquals(
			setOf(
				fixture.m1, fixture.m2, fixture.m3, fixture.m5,
				fixture.tc1, fixture.tr1, fixture.cp1, fixture.ur1,
			),
			messages.keys,
		)
		
		val user = messages.getValue(fixture.m1)
		assertEquals(AgentMessageType.USER.name, user[V1AgentMessageTable.type].name)
		assertEquals(fixture.t1, user[V1AgentMessageTable.timestamp])
		val decodedUser = decodeMessage(user)
		assertIs<AgentMessage.User>(decodedUser)
		assertEquals(setOf(fixture.a1), decodedUser.origin)
		assertEquals(
			V1MigrationTestEnv.MARKER,
			decodedUser.content.content!!.filterIsInstance<ContentPart.Text>().single().content,
		)
		
		val assistant = messages.getValue(fixture.m2)
		assertEquals(AgentMessageType.ASSISTANT.name, assistant[V1AgentMessageTable.type].name)
		assertEquals(fixture.t2, assistant[V1AgentMessageTable.timestamp])
		val decodedAssistant = decodeMessage(assistant)
		assertIs<AgentMessage.Assistant>(decodedAssistant)
		assertEquals("reply", decodedAssistant.content)
		assertEquals(fixture.modelId, decodedAssistant.model)
		assertEquals(setOf(fixture.a1), decodedAssistant.origin)
		
		assertEquals(setOf(fixture.a3), decodeMessage(messages.getValue(fixture.m3)).origin)
		
		assertEquals(
			setOf(
				fixture.m1 to fixture.a1,
				fixture.m2 to fixture.a1,
				fixture.m3 to fixture.a3,
				fixture.m5 to fixture.a1,
				fixture.tc1 to fixture.a1,
				fixture.tr1 to fixture.a1,
				fixture.cp1 to fixture.a1,
				fixture.ur1 to fixture.a1,
			),
			readOwnership(),
		)
		
		val tables = tablesOf("Sessions")
		assertFalse(tables.contains("session_message_v0"))
		assertFalse(tables.contains("_mig_owner"))
	}
	
	@Test
	fun `tool, compact and usage messages keep their payloads`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val messages = readMessages()
		
		val callRow = messages.getValue(fixture.tc1)
		assertEquals(AgentMessageType.TOOL_CALL.name, callRow[V1AgentMessageTable.type].name)
		val call = decodeMessage(callRow)
		assertIs<AgentMessage.Tool.Call>(call)
		assertEquals("toolu_1", call.callId)
		assertEquals("bash", call.callName)
		assertEquals("""{"command":"toolcallmarker"}""", call.arguments)
		assertEquals("inspect files", call.reason)
		assertEquals("bash", call.validatedToolName)
		assertEquals("""{"command":"toolcallmarker"}""", call.validatedArgs.toString())
		assertEquals(setOf(fixture.a1), call.origin)
		assertIs<UiBlock.Command>(call.presentation!!.single())
		
		val resultRow = messages.getValue(fixture.tr1)
		assertEquals(AgentMessageType.TOOL_RESULT.name, resultRow[V1AgentMessageTable.type].name)
		val result = decodeMessage(resultRow)
		assertIs<AgentMessage.Tool.Result>(result)
		assertEquals("toolresultmarker", result.content)
		assertEquals("file.txt", result.data!!.jsonObject.getValue("name").jsonPrimitive.content)
		assertEquals(ToolResultStatus.SUCCESS, result.status)
		assertIs<UiBlock.Output>(result.presentation.single())
		
		val compactRow = messages.getValue(fixture.cp1)
		assertEquals(AgentMessageType.COMPACT.name, compactRow[V1AgentMessageTable.type].name)
		val compact = decodeMessage(compactRow)
		assertIs<AgentMessage.Compact>(compact)
		assertEquals("compactsummarymarker", compact.content)
		assertEquals(fixture.modelId, compact.model)
		assertEquals(
			Usage(promptTokens = 11, completionTokens = 22, reasoningTokens = 3, cacheHitTokens = 4),
			compact.usage,
		)
		
		val usageRow = messages.getValue(fixture.ur1)
		assertEquals(AgentMessageType.USAGE_RECORD.name, usageRow[V1AgentMessageTable.type].name)
		val usage = decodeMessage(usageRow)
		assertIs<AgentMessage.UsageRecord>(usage)
		assertEquals(Usage(promptTokens = 5, completionTokens = 6), usage.usage)
	}
	
	@Test
	fun `user message keeps injections and media parts`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val message = decodeMessage(readMessages().getValue(fixture.m5))
		assertIs<AgentMessage.User>(message)
		assertEquals("ctx", message.content.injections!!.single().tag)
		assertEquals("injected", message.content.injections!!.single().content)
		
		val parts = message.content.content!!
		assertEquals("mixed", (parts[0] as ContentPart.Text).content)
		assertEquals("image/png", (parts[1] as ContentPart.Image).mimeType)
		assertEquals(Sha256("a".repeat(64)), (parts[1] as ContentPart.Image).data)
		assertEquals("https://example.com/a.png", (parts[2] as ContentPart.ImageUrl).url.value)
	}
	
	@Test
	fun `sessions and agents get message derived times`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val sessions = readSessions()
		assertEquals(setOf(fixture.s1, fixture.s2, fixture.s3), sessions.keys)
		val s1 = sessions.getValue(fixture.s1)
		assertEquals(fixture.t1, s1[V1SessionDataTable.creationTime])
		assertEquals(fixture.t2, s1[V1SessionDataTable.lastAccessTime])
		assertEquals(fixture.workspaceId, s1[V1SessionDataTable.workspaceId])
		assertEquals(fixture.a1, json.decodeFromJsonElement<AgentIndex>(s1[V1SessionDataTable.agentIndex]).main.id)
		
		val s3 = sessions.getValue(fixture.s3)
		assertEquals(fixture.t3, s3[V1SessionDataTable.creationTime])
		assertEquals(fixture.t3, s3[V1SessionDataTable.lastAccessTime])
		
		val agents = readAgents()
		assertEquals(setOf(fixture.a1, fixture.a2, fixture.a3), agents.keys)
		val a1 = agents.getValue(fixture.a1)
		assertEquals(fixture.s1, a1.sessionId)
		assertEquals("main", a1.name)
		assertEquals(fixture.t1, a1.creationTime)
		assertEquals(fixture.t2, a1.lastAccessTime)
		assertTrue(a1.activeTools.isEmpty())
		assertEquals(fixture.modelId, a1.model.model)
		assertEquals(setOf(fixture.m1, fixture.m2), a1.context.index.ids())
		
		assertEquals(fixture.s3, agents.getValue(fixture.a3).sessionId)
		assertEquals(fixture.s2, agents.getValue(fixture.a2).sessionId)
		
		val tables = tablesOf("Sessions")
		assertFalse(tables.contains("session_data_v0"))
		assertFalse(tables.contains("agent_data_v0"))
	}
	
	@Test
	fun `agent without messages falls back to migration time`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val session = readSessions().getValue(fixture.s2)
		val creation = session[V1SessionDataTable.creationTime]
		val lastAccess = session[V1SessionDataTable.lastAccessTime]
		assertTrue(creation > fixture.t3, "session fallback time $creation is not after the newest message")
		assertTrue(lastAccess >= creation, "session last access $lastAccess precedes creation $creation")
		
		val agent = readAgents().getValue(fixture.a2)
		assertEquals(creation, agent.creationTime)
		assertEquals(lastAccess, agent.lastAccessTime)
		assertTrue(agent.context.index.ids().isEmpty())
	}
	
	@Test
	fun `search index is rebuilt`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		V1DataMigration().migrate()
		
		val hits = MessageSearch.search(V1MigrationTestEnv.MARKER, null, null, null)
		assertTrue(hits.contains(fixture.m1))
		assertFalse(hits.contains(fixture.m4), "the orphan message was indexed")
		
		assertFalse(
			MessageSearch.search(V1MigrationTestEnv.MARKER, AgentMessageType.ASSISTANT, null, null)
				.contains(fixture.m1)
		)
		assertTrue(
			MessageSearch.search(V1MigrationTestEnv.MARKER, AgentMessageType.USER, fixture.t1, fixture.t1)
				.contains(fixture.m1)
		)
		assertFalse(
			MessageSearch.search(V1MigrationTestEnv.MARKER, AgentMessageType.USER, fixture.t2, null)
				.contains(fixture.m1)
		)
		assertFalse(MessageSearch.search("orphan", null, null, null).contains(fixture.m4))
		
		assertTrue(MessageSearch.search("toolcallmarker", null, null, null).contains(fixture.tc1))
		assertTrue(MessageSearch.search("toolresultmarker", null, null, null).contains(fixture.tr1))
		assertTrue(MessageSearch.search("compactsummarymarker", null, null, null).contains(fixture.cp1))
	}
	
	@Test
	fun `messages are migrated across multiple batches`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0(bulkMessages = 600)
		V1DataMigration().migrate()
		
		val messages = readMessages()
		assertEquals(8 + fixture.bulkIds.size, messages.size)
		assertTrue(messages.keys.containsAll(fixture.bulkIds))
		
		val owners = readOwnership()
		assertEquals(fixture.bulkIds.size, owners.count { it.second == fixture.bulkAgent })
		
		val session = readSessions().getValue(fixture.bulkSession)
		assertEquals(fixture.bulkStart, session[V1SessionDataTable.creationTime])
		assertEquals(
			fixture.bulkStart + (fixture.bulkIds.size - 1).seconds,
			session[V1SessionDataTable.lastAccessTime],
		)
	}
	
	@Test
	fun `missing sessions database migrates app config only`() = runBlocking {
		V1MigrationTestEnv.buildV0(withSessions = false)
		V1DataMigration().migrate()
		
		assertEquals("hello", settingValue("test.string"))
		assertFalse(Files.exists(DB_PATH.resolve("Sessions.mv.db")))
	}
	
	@Test
	fun `empty sessions database migrates to empty tables`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0(emptySessions = true)
		V1DataMigration().migrate()
		
		assertTrue(readSessions().isEmpty())
		assertTrue(readAgents().isEmpty())
		assertTrue(readMessages().isEmpty())
		assertTrue(readOwnership().isEmpty())
		assertEquals(
			setOf("session_data", "agent_data", "agent_message", "message_ownership"),
			tablesOf("Sessions"),
		)
		
		val workspace = readWorkspaces().getValue(fixture.workspaceId)
		assertTrue(workspace.sessionIds.isEmpty())
		assertTrue(workspace.creationTime > fixture.t3)
		assertTrue(workspace.lastAccessTime >= workspace.creationTime)
	}
	
	@Test
	fun `agent without owning session fails migration`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		insertAgent(UUID.randomUUID(), fixture.modelId)
		
		val error = try {
			V1DataMigration().migrate()
			null
		} catch (e: Throwable) {
			e
		}
		assertIs<IllegalStateException>(error)
		assertTrue(error.message!!.contains("Orphan agent"))
	}
	
	@Test
	fun `session without main agent fails migration`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		insertSession(UUID.randomUUID(), UUID.randomUUID(), fixture.workspaceId)
		
		val error = try {
			V1DataMigration().migrate()
			null
		} catch (e: Throwable) {
			e
		}
		assertIs<IllegalStateException>(error)
		assertTrue(error.message!!.contains("Missing main agent"))
	}
	
	@Test
	fun `session with child agents fails migration`() = runBlocking {
		val fixture = V1MigrationTestEnv.buildV0()
		insertSession(UUID.randomUUID(), fixture.a1, fixture.workspaceId, listOf(UUID.randomUUID()))
		
		val error = try {
			V1DataMigration().migrate()
			null
		} catch (e: Throwable) {
			e
		}
		assertIs<IllegalStateException>(error)
		assertTrue(error.message!!.contains("Unexpected child agents"))
	}
	
	private suspend fun settingKeys(): Set<String> = V1MigrationTestEnv.inAppConfig {
		V1SettingsTable.selectAll().map { it[V1SettingsTable.keyName] }.toSet()
	}
	
	private suspend fun settingValue(key: String): Any {
		val row = assertNotNull(settingRow(key), "missing setting $key")
		return settingColumns.mapNotNull { it(row) }.single()
	}
	
	private suspend fun settingRow(key: String): ResultRow? = V1MigrationTestEnv.inAppConfig {
		V1SettingsTable.selectAll().where { V1SettingsTable.keyName eq key }.singleOrNull()
	}
	
	private suspend fun readWorkspaces(): Map<UUID, WorkspaceData> = json.decodeFromJsonElement(
		MapSerializer(UuidSerializer, WorkspaceData.serializer()),
		readJson(V1MigrationTestEnv.WORKSPACE_NAMESPACE),
	)
	
	private suspend fun readJson(namespace: String): JsonElement {
		val element = V1MigrationTestEnv.inAppConfig {
			JsonStoreTable.selectAll().where { JsonStoreTable.namespace eq namespace }
				.singleOrNull()?.get(JsonStoreTable.content)
		}
		return assertNotNull(element, "missing json store namespace $namespace")
	}
	
	private suspend fun readMessages(): Map<UUID, ResultRow> = V1MigrationTestEnv.inSessions {
		V1AgentMessageTable.selectAll().associateBy { it[V1AgentMessageTable.id] }
	}
	
	private suspend fun readOwnership(): Set<Pair<UUID, UUID>> = V1MigrationTestEnv.inSessions {
		V1MessageOwnershipTable.selectAll()
			.map { it[V1MessageOwnershipTable.messageId] to it[V1MessageOwnershipTable.agentId] }
			.toSet()
	}
	
	private suspend fun readSessions(): Map<UUID, ResultRow> = V1MigrationTestEnv.inSessions {
		V1SessionDataTable.selectAll().associateBy { it[V1SessionDataTable.id] }
	}
	
	private suspend fun readAgents(): Map<UUID, AgentRow> = V1MigrationTestEnv.inSessions {
		V1AgentDataTable.selectAll().associate { row ->
			row[V1AgentDataTable.id] to AgentRow(
				sessionId = row[V1AgentDataTable.sessionId],
				name = row[V1AgentDataTable.name],
				creationTime = row[V1AgentDataTable.creationTime],
				lastAccessTime = row[V1AgentDataTable.lastAccessTime],
				activeTools = row[V1AgentDataTable.activeTools],
				model = json.decodeFromJsonElement(row[V1AgentDataTable.model]),
				context = json.decodeFromJsonElement(row[V1AgentDataTable.context]),
			)
		}
	}
	
	private fun decodeMessage(row: ResultRow): AgentMessage =
		json.decodeFromJsonElement(row[V1AgentMessageTable.content])
	
	private suspend fun tablesOf(dbName: String): Set<String> = V1MigrationTestEnv.inDatabase(dbName) {
		exec<Set<String>>("SELECT table_name FROM information_schema.tables WHERE lower(table_schema) = 'public'") { rs ->
			buildSet { while (rs.next()) add(rs.getString(1).lowercase()) }
		}.orEmpty()
	}
	
	private suspend fun constraintNames(dbName: String, table: String): Set<String> =
		V1MigrationTestEnv.inDatabase(dbName) {
			exec<Set<String>>(
				"SELECT constraint_name FROM information_schema.table_constraints " +
						"WHERE lower(table_name) = '$table' AND constraint_type = 'CHECK'"
			) { rs ->
				buildSet { while (rs.next()) add(rs.getString(1)) }
			}.orEmpty()
		}
	
	private suspend fun insertAgent(id: UUID, modelId: UUID) = V1MigrationTestEnv.inSessions {
		val agents = V0AgentTable("agent_data")
		agents.insert {
			it[agents.id] = id
			it[name] = "main"
			it[modelJson] = json.encodeToString(
				V0ModelConfig.serializer(),
				V0ModelConfig(modelId, null, modelId, modelId, emptyList()),
			)
			it[contextJson] = json.encodeToString(
				V0AgentContext.serializer(),
				V0AgentContext("system", null, V0AgentContextIndex(null, null, null), null),
			)
			it[activeToolsJson] = "[]"
		}
	}
	
	private suspend fun insertSession(
		id: UUID,
		mainId: UUID,
		workspaceId: UUID,
		children: List<UUID> = emptyList(),
	) = V1MigrationTestEnv.inSessions {
		val sessions = V0SessionTable("session_data")
		sessions.insert {
			it[sessions.id] = id
			it[title] = "stray"
			it[overview] = null
			it[sessions.workspaceId] = workspaceId
			it[agentIndexJson] = json.encodeToString(
				V0AgentIndex.serializer(),
				V0AgentIndex(
					V0AgentIndex.AgentNode(
						mainId,
						children.map { child -> V0AgentIndex.AgentNode(child, emptyList()) },
					)
				),
			)
		}
	}
	
	private data class AgentRow(
		val sessionId: UUID,
		val name: String,
		val creationTime: Instant,
		val lastAccessTime: Instant,
		val activeTools: List<String>,
		val model: ModelConfig,
		val context: AgentContext,
	)
	
	private companion object {
		const val TRACE_MAX_TOTAL_ENTRIES =
			"io.github.autotweaker.core.infrastructure.persist.db.trace.TraceSettings.MaxTotalEntries"
	}
}
