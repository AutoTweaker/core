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

import io.github.autotweaker.api.types.adapter.AdapterInfo

/**
 * AutoTweaker 的适配器，允许访问 [CoreAPI] 来将 AutoTweaker 的能力接入任何地方。
 *
 * 需要打上 `@AutoService(Adapter::class)` 来让 AutoTweaker 发现。
 *
 * 插件（包括适配器）的 jar 将被 AutoTweaker 使用同一个 `URLClassLoader` 加载，无论实现什么接口。
 * 这意味着插件内部即使实现了不同接口也可以互相访问，插件之间也可以互相访问。
 *
 * 甚至被 AutoTweaker 加载的插件实现还可以通过 `ServiceLoader.load(T::class.java, this::class.java.classLoader)` 加载其他插件的类。
 */
interface Adapter {
	/**
	 * 确保反映了适配器的实际运行状态，与 [start] / [stop] 控制的生命周期匹配。
	 */
	val isRunning: Boolean
	
	/**
	 * 初始化适配器，请不要在此时启动适配器，只做初始化工作。
	 */
	suspend fun init(core: CoreAPI): AdapterInfo
	
	/**
	 * 启动适配器，请确保方法返回后 [isRunning] 为 true，请确保在启动后可以通过 [stop] 停止。
	 *
	 * 如果 [isRunning]，AutoTweaker 不可能调用此方法，除非 [isRunning] 的变动不符合 AutoTweaker 的预期。
	 * 例如：[start] 后一段时间才 [isRunning] 或 [start] / [stop] 均未被调用但适配器自行变更 [isRunning]。
	 */
	suspend fun start()
	
	/**
	 * 停止适配器，AutoTweaker 退出前必然调用此方法，请确保停止事件循环以及协程，请确保在停止后仍然能够再次通过 [start] 启动。
	 *
	 * 如果 [isRunning] 为 false，AutoTweaker 不可能调用此方法，除非 [isRunning] 的变动不符合 AutoTweaker 的预期。
	 * 例如：[start] 后一段时间才 [isRunning] 或 [start] / [stop] 均未被调用但适配器自行变更 [isRunning]。
	 */
	suspend fun stop()
}
