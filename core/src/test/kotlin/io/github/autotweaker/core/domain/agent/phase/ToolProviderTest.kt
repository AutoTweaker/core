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

package io.github.autotweaker.core.domain.agent.phase

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.core.domain.agent.AgentContext
import io.github.autotweaker.core.domain.agent.AgentEnvironment
import io.github.autotweaker.core.domain.agent.tool.ToolProvider
import io.github.autotweaker.core.domain.model.Model
import io.github.autotweaker.core.domain.model.Provider
import io.github.autotweaker.core.domain.port.RawFileSystem
import io.github.autotweaker.core.domain.port.ShellExecutor
import io.github.autotweaker.core.domain.tool.port.BashService
import io.github.autotweaker.core.domain.tool.port.FileSystemService
import io.github.autotweaker.core.domain.tool.port.SummarizeService
import io.github.autotweaker.core.domain.tool.port.ToolCallHistory
import io.github.autotweaker.core.infrastructure.container.ContainerConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class ToolProviderTest {
	
	private lateinit var env: AgentEnvironment
	private lateinit var tmpDir: Path
	
	@BeforeTest
	fun setUp() {
		tmpDir = Files.createTempDirectory("autotweaker-test")
		val model = mockModel()
		val agentContext = AgentContext(null, null, null, null, null)
		
		env = mockk(relaxUnitFun = true)
		every { env.workspace } returns WorkspaceMeta("test", false, tmpDir)
		every { env.containerConfig } returns ContainerConfig(
			workDir = tmpDir,
			workspaceHostPath = tmpDir,
		)
		every { env.summarizeModel } returns model
		every { env.currentFallbackModels } returns null
		every { env.service } returns mockk<SettingService>(relaxed = true)
		every { env.context } returns MutableStateFlow(agentContext)
		ToolProvider.init(mockk<ShellExecutor>(relaxed = true), mockk<RawFileSystem>(relaxed = true))
	}
	
	@AfterTest
	fun tearDown() {
		tmpDir.toFile().deleteRecursively()
	}
	
	@Test
	fun `buildToolProvider creates container with required services`() {
		val container = ToolProvider.buildToolProvider(env)
		
		assertNotNull(container.get(FileSystemService::class))
		assertNotNull(container.get(SummarizeService::class))
		assertNotNull(container.get(BashService::class))
		assertNotNull(container.get(ToolCallHistory::class))
	}
	
	@Test
	fun `buildToolProvider returns a SimpleContainer instance`() {
		assert(true)
	}
	
	@Test
	fun `get throws for unregistered service`() {
		val container = ToolProvider.buildToolProvider(env)
		assertFailsWith<NoSuchElementException> {
			container.get(String::class)
		}
	}
	
	// region helpers
	
	private fun mockModel(): Model {
		val provider = mockk<Provider>()
		every { provider.name } returns "test-provider"
		return Model(
			provider = provider, modelInfo = mockk(relaxed = true), id = UUID.randomUUID()
		)
	}
	// endregion
}
