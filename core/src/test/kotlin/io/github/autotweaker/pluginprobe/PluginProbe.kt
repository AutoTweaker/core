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

package io.github.autotweaker.pluginprobe

import io.github.autotweaker.api.PLUGIN_PATH
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.pluginprobe.PluginProbe.ERROR_EXIT_CODE
import kotlin.system.exitProcess

/**
 * 在独立 JVM 中运行 [PluginLoader] 的探针，由 `PluginLoaderTest` 作为子进程驱动。
 *
 * [PluginLoader] 的可观测状态都是进程级的：扫描不到插件 jar 时会 `exitProcess(1)`，应用版本取自其所在包的
 * `Implementation-Version`，`sharedClassLoader` 是进程内单例。这些状态在同一个测试 JVM 里无法逐场景摆布，
 * 因此由本探针在子进程中执行真实的 [PluginLoader.load]，并以 stdout 上的 `key=value` 行回传观测结果。
 *
 * 本类不能声明在 `io.github.autotweaker.core` 包中：包版本由该包第一个被定义的 class 决定，
 * 探针若落在这个包里，会先把包定义成来自测试 classpath、没有 `Implementation-Version` 的版本。
 */
object PluginProbe {
	/** 应用版本，值取自 [PluginLoader] 所在包的 `Implementation-Version`。 */
	const val APP_VERSION_LINE = "APP_VERSION="
	
	/** [PLUGIN_PATH] 中的每个插件目录。 */
	const val PLUGIN_DIR_LINE = "PLUGIN_DIR="
	
	/** 每个被加载的插件实现类简单名。 */
	const val PLUGIN_LINE = "PLUGIN="
	
	/** 已到达加载调用。 */
	const val STARTED_LINE = "PROBE_STARTED"
	
	/** 加载已完成。 */
	const val OK_LINE = "PROBE_OK"
	
	/** 加载抛出异常，退出码为 [ERROR_EXIT_CODE]。 */
	const val ERROR_LINE = "PROBE_ERROR="
	
	private const val ERROR_EXIT_CODE = 2
	
	@JvmStatic
	fun main(args: Array<String>) {
		println(APP_VERSION_LINE + PluginLoader::class.java.getPackage().implementationVersion)
		PLUGIN_PATH.forEach { println(PLUGIN_DIR_LINE + it) }
		println(STARTED_LINE)
		
		// 子进程中 trace 服务未初始化，这里的 runCatching 只用于把加载异常与 exitProcess 的退出码区分开
		val plugins = runCatching { PluginLoader.load<ProbePlugin>() }.getOrElse { failure ->
			println(ERROR_LINE + failure::class.java.name + ": " + failure.message)
			exitProcess(ERROR_EXIT_CODE)
		}
		plugins.forEach { println(PLUGIN_LINE + it.javaClass.simpleName) }
		println(OK_LINE)
	}
}
