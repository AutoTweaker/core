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

package io.github.autotweaker.core.tool.impl.bash

import io.github.autotweaker.core.data.json.JsonStore
import io.github.autotweaker.core.data.settings.SettingItem
import io.github.autotweaker.core.data.settings.SettingKey
import io.github.autotweaker.core.session.workspace.Workspace
import io.github.autotweaker.core.tool.SimpleContainer
import io.github.autotweaker.core.tool.Tool
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.test.*

class BashTest {
	
	// region helpers
	
	private lateinit var bash: Bash
	private var storedJson: JsonElement? = null
	
	@BeforeTest
	fun setUp() {
		storedJson = null
		mockkObject(JsonStore)
		val mockEntry = mockk<JsonStore.JsonEntry>()
		every { mockEntry.get() } answers { storedJson }
		every { mockEntry.set(any()) } answers { storedJson = firstArg() }
		every { JsonStore.namespace(any()) } returns mockEntry
		bash = Bash()
	}
	
	@AfterTest
	fun tearDown() {
		unmockkObject(JsonStore)
	}
	
	private val defaultSettings: List<SettingItem> by lazy {
		listOf(
			setting("core.tool.bash.description", "Execute bash commands"),
			setting("core.tool.bash.function.description.run", "Run a bash command with timeout %d seconds"),
			setting("core.tool.bash.property.description.command", "The bash command to execute"),
			setting("core.tool.bash.property.description.timeout.seconds", "Timeout in seconds (default %d)"),
			setting("core.tool.bash.property.description.env.ids", "Environment variable IDs: %s"),
			SettingItem(SettingKey("core.tool.bash.setting.default.timeout.seconds"), SettingItem.Value.ValInt(30), ""),
			setting("core.tool.bash.message.error.invalid.timeout", "Timeout must be positive"),
			setting("core.tool.bash.message.error.invalid.command", "Command must not be blank"),
			setting(
				"core.tool.bash.message.result.template",
				"Exit code: %d\nDuration: %s seconds\n\nStdout:\n%s\n\nStderr:\n%s"
			),
		)
	}
	
	private fun setting(key: String, value: String): SettingItem =
		SettingItem(SettingKey(key), SettingItem.Value.ValString(value), "")
	
	private fun ToolInput(
		arguments: JsonObject,
		provider: SimpleContainer,
		settings: List<SettingItem> = defaultSettings,
	): Tool.ToolInput = Tool.ToolInput(
		functionName = "run",
		arguments = arguments,
		provider = provider,
		settings = settings,
		workspace = Workspace("test", false, Path.of("/tmp/test")),
		outputChannel = null,
	)
	
	private fun container(bashService: BashService): SimpleContainer {
		val c = SimpleContainer()
		c.register(BashService::class, bashService)
		return c
	}
	
	private fun args(
		command: String,
		timeoutSeconds: Int? = null,
		envIds: List<String>? = null,
	): JsonObject = buildJsonObject {
		put("command", JsonPrimitive(command))
		if (timeoutSeconds != null) put("timeout_seconds", JsonPrimitive(timeoutSeconds))
		if (envIds != null) put("env_ids", JsonArray(envIds.map { JsonPrimitive(it) }))
	}
	
	// endregion
	
	// region resolveMeta
	
	@Test
	fun `resolveMeta returns correct name`() {
		val meta = bash.resolveMeta(defaultSettings)
		assertEquals("bash", meta.name)
	}
	
	@Test
	fun `resolveMeta returns one function named run`() {
		val meta = bash.resolveMeta(defaultSettings)
		assertEquals(1, meta.functions.size)
		assertEquals("run", meta.functions.first().name)
	}
	
	@Test
	fun `resolveMeta run function has required command string parameter`() {
		val meta = bash.resolveMeta(defaultSettings)
		val runFunc = meta.functions.first()
		val command = runFunc.parameters["command"]!!
		assertTrue(command.required)
		assertTrue(command.valueType is Tool.Function.Property.ValueType.StringValue)
	}
	
	@Test
	fun `resolveMeta run function has optional timeout_seconds integer parameter`() {
		val meta = bash.resolveMeta(defaultSettings)
		val runFunc = meta.functions.first()
		val timeout = runFunc.parameters["timeout_seconds"]!!
		assertFalse(timeout.required)
		assertTrue(timeout.valueType is Tool.Function.Property.ValueType.IntegerValue)
	}
	
	@Test
	fun `resolveMeta run function has optional env_ids array parameter`() {
		val meta = bash.resolveMeta(defaultSettings)
		val runFunc = meta.functions.first()
		val envIds = runFunc.parameters["env_ids"]!!
		assertFalse(envIds.required)
		assertTrue(envIds.valueType is Tool.Function.Property.ValueType.ArrayValue)
	}
	
	@Test
	fun `resolveMeta timeout parameter description contains formatted default timeout`() {
		val meta = bash.resolveMeta(defaultSettings)
		val runFunc = meta.functions.first()
		val timeout = runFunc.parameters["timeout_seconds"]!!
		assertTrue(timeout.description.contains("30"))
	}
	
	@Test
	fun `resolveMeta env_ids description references available envs`() {
		val meta = bash.resolveMeta(defaultSettings)
		val runFunc = meta.functions.first()
		val envIds = runFunc.parameters["env_ids"]!!
		assertTrue(envIds.description.contains("<none>"))
	}
	
	// endregion
	
	// region execute - command validation
	
	@Test
	fun `blank command returns error`() = runTest {
		val bashService = mockk<BashService>()
		val input = ToolInput(args("   "), container(bashService))
		val result = bash.execute(input)
		
		assertFalse(result.success)
		assertEquals("Command must not be blank", result.result)
	}
	
	@Test
	fun `empty command returns error`() = runTest {
		val bashService = mockk<BashService>()
		val input = ToolInput(args(""), container(bashService))
		val result = bash.execute(input)
		
		assertFalse(result.success)
		assertEquals("Command must not be blank", result.result)
	}
	
	// endregion
	
	// region execute - timeout validation
	
	@Test
	fun `timeout zero returns error`() = runTest {
		val bashService = mockk<BashService>()
		val input = ToolInput(args("echo hello", timeoutSeconds = 0), container(bashService))
		val result = bash.execute(input)
		
		assertFalse(result.success)
		assertEquals("Timeout must be positive", result.result)
	}
	
	@Test
	fun `negative timeout returns error`() = runTest {
		val bashService = mockk<BashService>()
		val input = ToolInput(args("echo hello", timeoutSeconds = -5), container(bashService))
		val result = bash.execute(input)
		
		assertFalse(result.success)
		assertEquals("Timeout must be positive", result.result)
	}
	
	// endregion
	
	// region execute - successful runs
	
	@Test
	fun `successful command returns success true`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo hello", 30, emptyMap()) } returns BashService.Result(
			exitCode = 0, stdout = "hello", stderr = "", timeout = false, durationSeconds = 0.123
		)
		
		val input = ToolInput(args("echo hello"), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.success)
		assertTrue(result.result.contains("Exit code: 0"))
		assertTrue(result.result.contains("0.123"))
		assertTrue(result.result.contains("hello"))
	}
	
	@Test
	fun `non-zero exit code returns success false`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("false", 30, emptyMap()) } returns BashService.Result(
			exitCode = 1, stdout = "", stderr = "error msg", timeout = false, durationSeconds = 0.05
		)
		
		val input = ToolInput(args("false"), container(bashService))
		val result = bash.execute(input)
		
		assertFalse(result.success)
		assertTrue(result.result.contains("Exit code: 1"))
	}
	
	@Test
	fun `timeout returns success false`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("sleep 100", 1, emptyMap()) } returns BashService.Result(
			exitCode = -1, stdout = "", stderr = "", timeout = true, durationSeconds = 1.0
		)
		
		val input = ToolInput(args("sleep 100", timeoutSeconds = 1), container(bashService))
		val result = bash.execute(input)
		
		assertFalse(result.success)
	}
	
	@Test
	fun `custom timeout is passed to BashService`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo hi", 60, emptyMap()) } returns BashService.Result(
			exitCode = 0, stdout = "hi", stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(args("echo hi", timeoutSeconds = 60), container(bashService))
		bash.execute(input)
		
		coVerify { bashService.run("echo hi", 60, emptyMap()) }
	}
	
	@Test
	fun `missing timeout uses default from settings`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo hi", 30, emptyMap()) } returns BashService.Result(
			exitCode = 0, stdout = "hi", stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(args("echo hi"), container(bashService))
		bash.execute(input)
		
		coVerify { bashService.run("echo hi", 30, emptyMap()) }
	}
	
	// endregion
	
	// region execute - output formatting
	
	@Test
	fun `empty stdout shows placeholder`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns BashService.Result(
			exitCode = 0, stdout = "", stderr = "some error", timeout = false, durationSeconds = 0.1
		)
		
		val input = ToolInput(args("cmd"), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.result.contains("<empty>"))
		assertTrue(result.result.contains("some error"))
	}
	
	@Test
	fun `empty stderr shows placeholder`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns BashService.Result(
			exitCode = 0, stdout = "out", stderr = "", timeout = false, durationSeconds = 0.1
		)
		
		val input = ToolInput(args("cmd"), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.result.contains("<empty>"))
		assertTrue(result.result.contains("out"))
	}
	
	@Test
	fun `output contains duration formatted to 3 decimal places`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns BashService.Result(
			exitCode = 0, stdout = "out", stderr = "", timeout = false, durationSeconds = 2.5
		)
		
		val input = ToolInput(args("cmd"), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.result.contains("2.500"))
	}
	
	@Test
	fun `output contains formatted result template sections`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns BashService.Result(
			exitCode = 0, stdout = "out", stderr = "err", timeout = false, durationSeconds = 0.001
		)
		
		val input = ToolInput(args("cmd"), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.result.contains("Stdout:"))
		assertTrue(result.result.contains("Stderr:"))
		assertTrue(result.result.contains("Duration:"))
	}
	
	// endregion
	
	// region execute - env_ids
	
	@Test
	fun `env_ids are passed to BashService`() = runTest {
		bash.setEnv("MY_VAR", "my_value")
		
		val bashService = mockk<BashService>()
		coEvery { bashService.run($$"echo $MY_VAR", 30, mapOf("MY_VAR" to "my_value")) } returns BashService.Result(
			exitCode = 0, stdout = "my_value", stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(
			args($$"echo $MY_VAR", envIds = listOf("MY_VAR")), container(bashService)
		)
		val result = bash.execute(input)
		
		assertTrue(result.success)
		coVerify { bashService.run($$"echo $MY_VAR", 30, mapOf("MY_VAR" to "my_value")) }
	}
	
	@Test
	fun `multiple env_ids are passed to BashService`() = runTest {
		bash.setEnv("A", "1")
		bash.setEnv("B", "2")
		
		val bashService = mockk<BashService>()
		coEvery { bashService.run(any(), any(), any()) } returns BashService.Result(
			exitCode = 0, stdout = "", stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(
			args("cmd", envIds = listOf("A", "B")), container(bashService)
		)
		bash.execute(input)
		
		coVerify { bashService.run("cmd", 30, mapOf("A" to "1", "B" to "2")) }
	}
	
	@Test
	fun `non-existent env_ids are ignored`() = runTest {
		bash.setEnv("EXISTING", "val")
		
		val bashService = mockk<BashService>()
		coEvery { bashService.run("cmd", 30, mapOf("EXISTING" to "val")) } returns BashService.Result(
			exitCode = 0, stdout = "", stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(
			args("cmd", envIds = listOf("EXISTING", "MISSING")), container(bashService)
		)
		bash.execute(input)
		
		coVerify { bashService.run("cmd", 30, mapOf("EXISTING" to "val")) }
	}
	
	// endregion
	
	// region execute - edge cases
	
	@Test
	fun `command with special characters works`() = runTest {
		val bashService = mockk<BashService>()
		coEvery { bashService.run("echo \"hello world\"", 30, emptyMap()) } returns BashService.Result(
			exitCode = 0, stdout = "hello world", stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(args("echo \"hello world\""), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.success)
	}
	
	@Test
	fun `very long command works`() = runTest {
		val longCmd = "echo " + "x".repeat(1000)
		val bashService = mockk<BashService>()
		coEvery { bashService.run(longCmd, 30, emptyMap()) } returns BashService.Result(
			exitCode = 0, stdout = "x".repeat(1000), stderr = "", timeout = false, durationSeconds = 0.01
		)
		
		val input = ToolInput(args(longCmd), container(bashService))
		val result = bash.execute(input)
		
		assertTrue(result.success)
	}
	
	// endregion
	
	// region env store management
	
	@Test
	fun `getEnv returns null for non-existent id`() {
		assertNull(bash.getEnv("NONEXISTENT"))
	}
	
	@Test
	fun `setEnv and getEnv roundtrip`() {
		bash.setEnv("MY_KEY", "my_value")
		assertEquals("my_value", bash.getEnv("MY_KEY"))
	}
	
	@Test
	fun `setEnv overwrites existing value`() {
		bash.setEnv("KEY", "old")
		bash.setEnv("KEY", "new")
		assertEquals("new", bash.getEnv("KEY"))
	}
	
	@Test
	fun `removeEnv deletes existing entry`() {
		bash.setEnv("KEY", "value")
		bash.removeEnv("KEY")
		assertNull(bash.getEnv("KEY"))
	}
	
	@Test
	fun `removeEnv is no-op for non-existent entry`() {
		assertNull(bash.getEnv("NONEXISTENT"))
		bash.removeEnv("NONEXISTENT")
		assertNull(bash.getEnv("NONEXISTENT"))
	}
	
	@Test
	fun `setEnv with special characters`() {
		bash.setEnv("KEY", "value with spaces and \"quotes\"")
		assertEquals("value with spaces and \"quotes\"", bash.getEnv("KEY"))
	}
	
	@Test
	fun `multiple env entries coexist`() {
		bash.setEnv("A", "1")
		bash.setEnv("B", "2")
		bash.setEnv("C", "3")
		assertEquals("1", bash.getEnv("A"))
		assertEquals("2", bash.getEnv("B"))
		assertEquals("3", bash.getEnv("C"))
	}
	
	@Test
	fun `resolveMeta env_ids shows available env IDs`() {
		bash.setEnv("DB_URL", "jdbc:...")
		bash.setEnv("API_KEY", "secret")
		
		val meta = bash.resolveMeta(defaultSettings)
		val envIdsDesc = meta.functions.first().parameters["env_ids"]!!.description
		assertTrue(envIdsDesc.contains("DB_URL"))
		assertTrue(envIdsDesc.contains("API_KEY"))
	}
	
	@Test
	fun `resolveMeta env_ids with quotes in name are escaped`() {
		bash.setEnv("KEY_WITH\"QUOTE", "val")
		
		val meta = bash.resolveMeta(defaultSettings)
		val envIdsDesc = meta.functions.first().parameters["env_ids"]!!.description
		assertTrue(envIdsDesc.contains("KEY_WITH\\\"QUOTE"))
	}
	
	// endregion
}
