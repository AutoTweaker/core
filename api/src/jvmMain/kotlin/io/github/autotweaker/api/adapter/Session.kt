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

package io.github.autotweaker.api.adapter

import io.github.autotweaker.api.types.agent.AgentIndex
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.exception.notfound.AgentNotFoundException
import io.github.autotweaker.api.types.exception.notfound.ModelNotFoundException
import io.github.autotweaker.api.types.exception.notfound.ProviderNotFoundException
import io.github.autotweaker.api.types.exception.notfound.SecretNotFoundException
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * 一个会话实例，一个会话中可能拥有多个 agent。
 */
interface Session {
	/**
	 * 会话的 [UUID]。
	 */
	val id: UUID
	
	/**
	 * 会话的工作区 id。
	 */
	val workspaceId: UUID
	
	/**
	 * 会话标题，可通过 [updateTitle] 更新。
	 */
	val title: StateFlow<String?>
	
	/**
	 * LLM 生成的会话概述，可向用户展示。
	 */
	val overview: StateFlow<String?>
	
	/**
	 * 会话中的所有 agent。
	 */
	val agentIndex: StateFlow<AgentIndex>
	
	/**
	 * 获取内存中的 [Agent] 实例。
	 */
	fun getOrNull(agent: UUID): Agent?
	
	/**
	 * 从持久化恢复 agent 实例，如果内存中已有会直接返回。
	 *
	 * @throws AgentNotFoundException
	 * @throws ModelNotFoundException
	 * @throws ProviderNotFoundException
	 * @throws SecretNotFoundException
	 * @throws SecretStoreLockedException
	 */
	suspend fun restore(agent: UUID): Agent
	
	/**
	 * 更新会话标题。
	 */
	fun updateTitle(function: (String?) -> String?)
}
