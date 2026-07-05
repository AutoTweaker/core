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

import io.github.autotweaker.api.types.exception.PathOutsideWorkspaceException
import java.nio.file.Path

/**
 * AutoTweaker 支持容器内工作区，并将宿主目录挂载到容器内以便程序直接读写宿主文件系统。
 *
 * 需要保障 LLM 始终以“容器内”的视角执行操作，而程序在读写文件系统时又能够正确得到宿主路径。
 *
 * 此接口用于处理容器内 <---> 宿主的路径映射关系。
 *
 * AutoTweaker 会挂载 [io.github.autotweaker.api.CONFIG_PATH] `/container/workspace` 以及 [io.github.autotweaker.api.TMP_PATH] `/container` 到容器内。
 */
interface PathResolver {
	/**
	 * 判断一个工作区是否为容器内工作区，只需提供 [Path]。
	 *
	 * 容器内工作区中的任何操作都只应该影响容器内状态，也包括宿主挂载路径的状态。
	 */
	fun inContainer(workspace: Path): Boolean
	
	/**
	 * 基于工作区路径解析相对路径为绝对路径，不会进行转换，容器内得到的是“容器内视角”的绝对路径，宿主得到的是“宿主视角”的绝对路径。
	 *
	 * @param workspace 工作区的路径，工作区路径始终为宿主路径，如果需要解析容器内路径，AutoTweaker 会自动映射挂载关系。
	 */
	fun toAbsolutePath(workspace: Path, path: Path): Path
	
	/**
	 * 将宿主路径转换为容器内路径，适用于保存文件后提供给 LLM 路径，或在容器内执行命令的场景。
	 *
	 * @throws PathOutsideWorkspaceException 提供的路径未被挂载到容器内。
	 */
	fun toContainerPath(path: Path): Path
	
	/**
	 * 将容器内路径转换为宿主路径，适用于根据 LLM 的请求读写宿主文件系统。
	 *
	 * @throws PathOutsideWorkspaceException 提供的路径超出了宿主挂载范围。
	 */
	fun toHostPath(path: Path): Path
}
