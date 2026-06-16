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

package io.github.autotweaker.api.types.agent

import io.github.autotweaker.api.types.agent.AgentIndex.AgentNode.Companion.findNode
import io.github.autotweaker.api.types.serializer.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class AgentIndex(
	val main: AgentNode,
) {
	@Serializable
	data class AgentNode(
		@Serializable(with = UuidSerializer::class)
		val id: UUID,
		val children: List<AgentNode>
	) {
		companion object {
			fun AgentNode.findNode(agentId: UUID): AgentNode? {
				if (id == agentId) return this
				children.forEach { child ->
					child.findNode(agentId)?.let { return it }
				}
				return null
			}
		}
	}
	
	companion object {
		fun AgentIndex.findChildren(agentId: UUID): List<AgentNode> =
			main.findNode(agentId)?.children.orEmpty()
		
		fun AgentIndex.addChild(parent: UUID, child: UUID): AgentIndex {
			fun AgentNode.replace(): AgentNode {
				if (id == parent) {
					return copy(children = children + AgentNode(id = child, children = emptyList()))
				}
				return copy(children = children.map { replace() })
			}
			return copy(main = main.replace())
		}
		
		fun emptyIndex() = AgentIndex(AgentNode(UUID.randomUUID(), emptyList()))
	}
}
