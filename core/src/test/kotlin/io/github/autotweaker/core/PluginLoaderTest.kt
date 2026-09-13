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

package io.github.autotweaker.core

import io.github.autotweaker.api.PLUGIN_PATH
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.core.infrastructure.data.ResourcesLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 端到端验证 [PluginLoader] 对插件 jar 的 apiVersion 兼容性判定。
 *
 * `PluginLoader.isApiCompatible` 是 private，只能通过 [PluginLoader.getOrCreateClassLoader] 返回的
 * [java.net.URLClassLoader] 暴露的 `urls` 间接观察判定结果：只有被接受的 jar 才会出现在 `urls` 中。
 *
 * 注意：[PluginLoader] 是 object，`sharedClassLoader` 是进程内单例且 `close()` 不会将其重置为 null，
 * 因此整个测试类只能调用一次 `getOrCreateClassLoader`，所有场景必须在同一个 @Test 方法内完成：
 * 先在 [PLUGIN_PATH] 铺好全部构造 jar，再调用一次，最后统一断言 `urls` 集合。
 */
class PluginLoaderTest {
	
	@Test
	fun `plugin jar api compatibility decides accepted jars`() {
		val core = ResourcesLoader.version
		
		// 与 core 完全相同：dev 构建形如 0.2.0-dev+1789224056.b41e827d，build metadata 不参与比较，故与 core 相等
		val exact = core.toString()
		// 三元组相同、预发布标识不同且严格早于 core：0.2.0-dev -> 0.2.0-alpha.5，用于命中 dev 通配符分支
		val sameTripleOlderPre = SemVer(core.major, core.minor, core.patch, olderPreRelease(core))
		// 比 core 新：major 更大即严格更新，任何 core 版本下都应被拒绝
		val newer = SemVer(core.major + 1, 0, 0)
		// 比 core 旧：core.major > 0 时为"更旧且 major 不同"，core.major == 0 时退化为"更旧且 major 相同"
		val older = olderVersion(core)
		
		// 前置条件：先确认构造出来的版本确实落在各自期望的位置，避免用例本身构造错误
		assertEquals(core, SemVer.parse(exact), "exact 应当与 core 相等，core=$core")
		assertTrue(sameTripleOlderPre < core, "sameTripleOlderPre 应当比 core 旧，$sameTripleOlderPre vs $core")
		assertTrue(newer > core, "newer 应当比 core 新，$newer vs $core")
		assertTrue(older < core, "older 应当比 core 旧，$older vs $core")
		
		// core 为 dev 构建（preRelease == ["dev"]）时只看三元组，预发布标识无关，因此同三元组即被接受；
		// 否则该版本比 core 旧，只有"同 major 兼容"规则（core.major > 0）才会接受它
		val sameTripleAccepted = core.preRelease == listOf("dev") || core.major > 0
		
		preparePluginPath()
		writePluginJar("exact.jar", exact)
		writePluginJar("same-triple-other-pre.jar", sameTripleOlderPre.toString())
		writePluginJar("newer.jar", newer.toString())
		writePluginJar("older.jar", older.toString())
		writePluginJar("missing-api-version.jar", null)
		writePluginJar("malformed-api-version.jar", "not-a-semver")
		
		val loader = PluginLoader.getOrCreateClassLoader(javaClass.classLoader)
			?: fail("getOrCreateClassLoader 返回 null，core=$core，PLUGIN_PATH=$PLUGIN_PATH 内文件=${pluginPathFiles()}")
		
		val expected = buildSet {
			add("exact.jar")
			if (sameTripleAccepted) add("same-triple-other-pre.jar")
		}
		val urls = loader.urLs
		val actual = urls.map { Path.of(it.toURI()).fileName.toString() }.toSet()
		
		assertEquals(
			expected.size, urls.size,
			"URLClassLoader 的 urls 中不应有重复 jar，core=$core，actual=$actual"
		)
		assertEquals(
			expected, actual,
			"URLClassLoader 的 urls 应当恰好是被接受的插件 jar，core=$core，sameTripleAccepted=$sameTripleAccepted"
		)
	}
	
	companion object {
		/**
		 * 构造一个与 [core] 三元组相同、预发布标识不同且严格早于 [core] 的预发布标识，
		 * 用于命中 dev 通配符分支（该分支只比较三元组，不比较预发布标识）。
		 */
		private fun olderPreRelease(core: SemVer): List<String> =
			listOf(listOf("alpha", "5"), listOf("0"), listOf("dev"))
				.firstOrNull { candidate ->
					val version = SemVer(core.major, core.minor, core.patch, candidate)
					version < core && version != core
				}
				?: fail("无法为 core 构造更旧的预发布标识，core=$core")
		
		/**
		 * 构造一个严格早于 [core] 的版本。SemVer 的 major 不能为负，因此 core.major > 0 时取
		 * `(major - 1).0.0`（更旧且 major 不同），core.major == 0 时退化为主版本相同、更旧的版本。
		 */
		private fun olderVersion(core: SemVer): SemVer = when {
			core.major > 0 -> SemVer(core.major - 1, 0, 0)
			core.minor > 0 -> SemVer(0, core.minor - 1, 0)
			core.patch > 0 -> SemVer(0, 0, core.patch - 1)
			else -> SemVer(0, 0, 0, listOf("0"))
		}
		
		/** 清空并重建 [PLUGIN_PATH]，保证扫描到的 jar 只有本用例构造的那些。 */
		private fun preparePluginPath() {
			if (Files.isDirectory(PLUGIN_PATH)) {
				Files.list(PLUGIN_PATH).use { paths -> paths.toList().forEach { it.deleteIfExists() } }
			}
			Files.createDirectories(PLUGIN_PATH)
		}
		
		/**
		 * 写入一个最小插件 jar：只写 `META-INF/autotweaker/plugin.properties`（[apiVersion] 为 null 时不写该条目）。
		 * PluginLoader 的 class 扫描对空集合是安全的，因此不需要真实 class 文件。
		 */
		private fun writePluginJar(fileName: String, apiVersion: String?) {
			JarOutputStream(Files.newOutputStream(PLUGIN_PATH.resolve(fileName))).use { jar ->
				if (apiVersion == null) {
					jar.putNextEntry(JarEntry("readme.txt"))
					jar.write("no plugin.properties".toByteArray())
				} else {
					jar.putNextEntry(JarEntry("META-INF/autotweaker/plugin.properties"))
					jar.write("apiVersion=$apiVersion\n".toByteArray())
				}
				jar.closeEntry()
			}
		}
		
		/** 列出 [PLUGIN_PATH] 下的文件名，仅用于失败信息。 */
		private fun pluginPathFiles(): List<String> =
			if (Files.isDirectory(PLUGIN_PATH)) {
				Files.list(PLUGIN_PATH).use { paths -> paths.map { it.fileName.toString() }.toList() }
			} else {
				emptyList()
			}
	}
}
