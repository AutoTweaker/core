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

package io.github.autotweaker.core.domain.tool.impl.bash

import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.ToolArgs
import io.github.autotweaker.api.types.config.SettingValue
import io.github.autotweaker.api.types.shell.ShellEvent
import io.github.autotweaker.api.types.shell.ShellResult
import io.github.autotweaker.api.types.tool.args.BashArgs
import io.github.autotweaker.core.TestServices
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.domain.tool.SimpleContainer
import io.github.autotweaker.core.domain.tool.ToolMeta
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.infrastructure.persistence.json.JsonStoreImpl
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class BashTest {
	companion object {
		init {
			TestServices.init()
		}
	}
	
	// region helpers
	
	private lateinit var bash: Bash
	private lateinit var secretStore: SecretStore
	private var storedJson: JsonElement? = null
	
	@BeforeTest
	fun setUp() {
		storedJson = null
		mockkObject(JsonStoreImpl)
		val mockEntry = mockk<JsonStore>()
		every { mockEntry.get() } answers { storedJson }
		every { mockEntry.set(any()) } answers { storedJson = firstArg<JsonElement>() }
		every { JsonStoreImpl.namespace(any()) } returns mockEntry
		
		val secretMap = mutableMapOf<UUID, String>()
		secretStore = object : SecretStore {
			override suspend fun add(secret: String, id: UUID): UUID = id.also { secretMap[it] = secret }
			override suspend fun get(id: UUID): String = secretMap[id]!!
			override fun list(): List<UUID> = secretMap.keys.toList()
			override fun remove(id: UUID) {
				secretMap.remove(id)
			}
		}
		
		bash = Bash()
		runBlocking { bash.init(secretStore) }
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(JsonStoreImpl)
	}
	
	
	private fun toolArgs(
		command: String,
		timeoutSeconds: Int? = null,
		envIds: List<String>? = null,
	): BashArgs = BashArgs(
		command = command,
		timeoutSeconds = timeoutSeconds ?: 60,
		envIds = envIds ?: emptyList(),
	)
	
	private fun container(bashService: BashService): SimpleContainer {
		val c = SimpleContainer()
		c.register(BashService::class, bashService)
		return c
	}
	
	private fun mockResult(
		exitCode: Int, stdout: String, stderr: String = "", timeout: Boolean = false, durationSeconds: Double = 0.01
	) = flowOf(
		ShellEvent.Stdout(if (stdout.isNotEmpty()) "$stdout\n" else ""),
		ShellEvent.Stderr(if (stderr.isNotEmpty()) "$stderr\n" else ""),
		ShellEvent.Exit(ShellResult(exitCode, timeout, durationSeconds.seconds)),
	)
	
	// endregion
	
	// region meta
	
	@Test
	fun `meta returns correct name`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		assertEquals("bash", meta.name)
	}
	
	@Test
	fun `meta returns one function named run`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		assertEquals(1, meta.functions.size)
		assertEquals("run", meta.functions.first().name)
	}
	
	@Test
	fun `meta run function has required command string parameter`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val runFunc = meta.functions.first()
		val command = runFunc.parameters["command"]!!
		assertTrue(command.required)
		assertTrue(command.valueType is ToolMeta.ValueType.StringValue)
	}
	
	@Test
	fun `meta run function has optional timeout_seconds integer parameter`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val runFunc = meta.functions.first()
		val timeout = runFunc.parameters["timeout_seconds"]!!
		assertFalse(timeout.required)
		assertTrue(timeout.valueType is ToolMeta.ValueType.IntegerValue)
	}
	
	@Test
	fun `meta run function has optional env_ids array parameter`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val runFunc = meta.functions.first()
		val envIds = runFunc.parameters["env_ids"]!!
		assertFalse(envIds.required)
		assertTrue(envIds.valueType is ToolMeta.ValueType.ArrayValue)
	}
	
	@Test
	fun `meta timeout parameter description contains formatted default timeout`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val runFunc = meta.functions.first()
		val timeout = runFunc.parameters["timeout_seconds"]!!
		assertTrue(timeout.description.contains("120"))
	}
	
	@Test
	fun `meta env_ids description references available envs`() = runTest {
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val runFunc = meta.functions.first()
		val envIds = runFunc.parameters["env_ids"]!!
		assertTrue(envIds.description.contains("[none]"))
	}
	
	// endregion
	
	// region execute - command validation
	
	@Test
	fun `blank command returns error`() = runTest {
		val bashService = mockk<BashService>()
		bash.init(secretStore)
		val args = toolArgs("   ")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertFalse(result.success)
		assertEquals("command参数不能为空", result.result)
	}
	
	@Test
	fun `empty command returns error`() = runTest {
		val bashService = mockk<BashService>()
		bash.init(secretStore)
		val args = toolArgs("")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertFalse(result.success)
		assertEquals("command参数不能为空", result.result)
	}
	
	// endregion
	
	// region execute - timeout validation
	
	@Test
	fun `timeout zero returns error`() = runTest {
		val bashService = mockk<BashService>()
		bash.init(secretStore)
		val args = toolArgs("echo hello", timeoutSeconds = 0)
		val result = bash.coreExec(container(bashService), args, null)
		
		assertFalse(result.success)
		assertEquals("timeout_seconds必须大于0", result.result)
	}
	
	@Test
	fun `negative timeout returns error`() = runTest {
		val bashService = mockk<BashService>()
		bash.init(secretStore)
		val args = toolArgs("echo hello", timeoutSeconds = -5)
		val result = bash.coreExec(container(bashService), args, null)
		
		assertFalse(result.success)
		assertEquals("timeout_seconds必须大于0", result.result)
	}
	
	// endregion
	
	// region execute - successful runs
	
	@Test
	fun `successful command returns success true`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo hello", 60.seconds, emptyMap()) } returns mockResult(
			exitCode = 0, stdout = "hello", durationSeconds = 0.123
		)
		bash.init(secretStore)
		val args = toolArgs("echo hello")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.success)
		assertTrue(result.result.contains("退出码：0"))
		assertTrue(result.result.contains("0.123"))
		assertTrue(result.result.contains("hello"))
	}
	
	@Test
	fun `non-zero exit code returns success false`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("false", 60.seconds, emptyMap()) } returns mockResult(
			exitCode = 1, stdout = "", stderr = "error msg", durationSeconds = 0.05
		)
		bash.init(secretStore)
		val args = toolArgs("false")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertFalse(result.success)
		assertTrue(result.result.contains("退出码：1"))
	}
	
	@Test
	fun `timeout returns success false`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("sleep 100", 1.seconds, emptyMap()) } returns mockResult(
			exitCode = -1, stdout = "", timeout = true, durationSeconds = 1.0
		)
		bash.init(secretStore)
		val args = toolArgs("sleep 100", timeoutSeconds = 1)
		val result = bash.coreExec(container(bashService), args, null)
		
		assertFalse(result.success)
	}
	
	@Test
	fun `custom timeout is passed to BashService`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo hi", 60.seconds, emptyMap()) } returns mockResult(
			exitCode = 0, stdout = "hi"
		)
		bash.init(secretStore)
		val args = toolArgs("echo hi", timeoutSeconds = 60)
		bash.coreExec(container(bashService), args, null)
		
		coVerify { bashService.run("echo hi", 60.seconds, emptyMap()) }
	}
	
	@Test
	fun `missing timeout uses default from settings`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo hi", 60.seconds, emptyMap()) } returns mockResult(
			exitCode = 0, stdout = "hi"
		)
		bash.init(secretStore)
		val args = toolArgs("echo hi")
		bash.coreExec(container(bashService), args, null)
		
		coVerify { bashService.run("echo hi", 60.seconds, emptyMap()) }
	}
	
	// endregion
	
	// region execute - output formatting
	
	@Test
	fun `empty stdout shows placeholder`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns mockResult(
			exitCode = 0, stdout = "", stderr = "some error", durationSeconds = 0.1
		)
		bash.init(secretStore)
		val args = toolArgs("cmd")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.result.contains("[empty]"))
		assertTrue(result.result.contains("some error"))
	}
	
	@Test
	fun `empty stderr shows placeholder`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns mockResult(
			exitCode = 0, stdout = "out", stderr = "", durationSeconds = 0.1
		)
		bash.init(secretStore)
		val args = toolArgs("cmd")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.result.contains("[empty]"))
		assertTrue(result.result.contains("out"))
	}
	
	@Test
	fun `output contains duration formatted to 3 decimal places`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns mockResult(
			exitCode = 0, stdout = "out", durationSeconds = 2.5
		)
		bash.init(secretStore)
		val args = toolArgs("cmd")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.result.contains("2.500"))
	}
	
	@Test
	fun `output contains formatted result template sections`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns mockResult(
			exitCode = 0, stdout = "out", stderr = "err", durationSeconds = 0.001
		)
		bash.init(secretStore)
		val args = toolArgs("cmd")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.result.contains("标准输出："))
		assertTrue(result.result.contains("标准错误："))
		assertTrue(result.result.contains("执行时间："))
	}
	
	// endregion
	
	// region execute - env_ids
	
	@Test
	fun `env_ids are passed to BashService`() = runTest {
		bash.setEnv("MY_VAR", "my_value")
		
		val bashService = mockk<BashService>()
		coEvery { bashService.run($$"echo $MY_VAR", 60.seconds, mapOf("MY_VAR" to "my_value")) } returns mockResult(
			exitCode = 0, stdout = "my_value"
		)
		bash.init(secretStore)
		val args = toolArgs($$"echo $MY_VAR", envIds = listOf("MY_VAR"))
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.success)
		coVerify { bashService.run($$"echo $MY_VAR", 60.seconds, mapOf("MY_VAR" to "my_value")) }
	}
	
	@Test
	fun `multiple env_ids are passed to BashService`() = runTest {
		bash.setEnv("A", "1")
		bash.setEnv("B", "2")
		
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns mockResult(exitCode = 0, stdout = "")
		bash.init(secretStore)
		val args = toolArgs("cmd", envIds = listOf("A", "B"))
		bash.coreExec(container(bashService), args, null)
		
		coVerify { bashService.run("cmd", 60.seconds, mapOf("A" to "1", "B" to "2")) }
	}
	
	@Test
	fun `non-existent env_ids are ignored`() = runTest {
		bash.setEnv("EXISTING", "val")
		
		val bashService = mockk<BashService>()
		coEvery { bashService.run("cmd", 60.seconds, mapOf("EXISTING" to "val")) } returns mockResult(
			exitCode = 0,
			stdout = ""
		)
		bash.init(secretStore)
		val args = toolArgs("cmd", envIds = listOf("EXISTING", "MISSING"))
		bash.coreExec(container(bashService), args, null)
		
		coVerify { bashService.run("cmd", 60.seconds, mapOf("EXISTING" to "val")) }
	}
	
	// endregion
	
	// region execute - edge cases
	
	@Test
	fun `command with special characters works`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo \"hello world\"", 60.seconds, emptyMap()) } returns mockResult(
			exitCode = 0, stdout = "hello world"
		)
		bash.init(secretStore)
		val args = toolArgs("echo \"hello world\"")
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.success)
	}
	
	@Test
	fun `very long command works`() = runTest {
		val longCmd = "echo " + "x".repeat(1000)
		val bashService = mockk<BashService>()
		coEvery { bashService.run(longCmd, 60.seconds, emptyMap()) } returns mockResult(
			exitCode = 0, stdout = "x".repeat(1000)
		)
		bash.init(secretStore)
		val args = toolArgs(longCmd)
		val result = bash.coreExec(container(bashService), args, null)
		
		assertTrue(result.success)
	}
	
	// endregion
	
	// region env store management
	
	@Test
	fun `getEnv returns null for non-existent id`() = runBlocking {
		assertNull(bash.getEnv("NONEXISTENT"))
	}
	
	@Test
	fun `setEnv and getEnv roundtrip`() = runBlocking {
		bash.setEnv("MY_KEY", "my_value")
		assertEquals("my_value", bash.getEnv("MY_KEY"))
	}
	
	@Test
	fun `setEnv overwrites existing value`() = runBlocking {
		bash.setEnv("KEY", "old")
		bash.setEnv("KEY", "new")
		assertEquals("new", bash.getEnv("KEY"))
	}
	
	@Test
	fun `removeEnv deletes existing entry`() = runBlocking {
		bash.setEnv("KEY", "value")
		bash.removeEnv("KEY")
		assertNull(bash.getEnv("KEY"))
	}
	
	@Test
	fun `removeEnv is no-op for non-existent entry`() = runBlocking {
		assertNull(bash.getEnv("NONEXISTENT"))
		bash.removeEnv("NONEXISTENT")
		assertNull(bash.getEnv("NONEXISTENT"))
	}
	
	@Test
	fun `setEnv with special characters`() = runBlocking {
		bash.setEnv("KEY", "value with spaces and \"quotes\"")
		assertEquals("value with spaces and \"quotes\"", bash.getEnv("KEY"))
	}
	
	@Test
	fun `multiple env entries coexist`() = runBlocking {
		bash.setEnv("A", "1")
		bash.setEnv("B", "2")
		bash.setEnv("C", "3")
		assertEquals("1", bash.getEnv("A"))
		assertEquals("2", bash.getEnv("B"))
		assertEquals("3", bash.getEnv("C"))
	}
	
	@Test
	fun `meta env_ids shows available env IDs`() = runTest {
		bash.setEnv("DB_URL", "jdbc:...")
		bash.setEnv("API_KEY", "secret")
		bash.init(secretStore)
		
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val envIdsDesc = meta.functions.first().parameters["env_ids"]!!.description
		assertTrue(envIdsDesc.contains("DB_URL"))
		assertTrue(envIdsDesc.contains("API_KEY"))
	}
	
	@Test
	fun `meta env_ids with quotes in name are escaped`() = runTest {
		bash.setEnv("KEY_WITH\"QUOTE", "val")
		bash.init(secretStore)
		
		@Suppress("UNCHECKED_CAST")
		val meta = ToolMeta.build(bash as Tool<ToolArgs>)
		val envIdsDesc = meta.functions.first().parameters["env_ids"]!!.description
		assertTrue(envIdsDesc.contains("KEY_WITH\\\"QUOTE"))
	}
	
	// endregion
}
