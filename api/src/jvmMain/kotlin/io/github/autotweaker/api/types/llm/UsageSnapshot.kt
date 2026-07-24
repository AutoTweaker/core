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

package io.github.autotweaker.api.types.llm

import kotlinx.serialization.Serializable

/**
 * LLM 花费信息。
 */
@Serializable
data class UsageSnapshot(
	/**
	 * 大模型 api 返回的花费数据。
	 */
	val usage: Usage,
	/**
	 * 产生花费时模型元数据快照，确保即使模型被删除或定价变动，用量信息依然能够准确计算。
	 */
	val model: ModelData.ModelInfo,
)
