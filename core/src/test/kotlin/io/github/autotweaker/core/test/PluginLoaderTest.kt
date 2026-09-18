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

package io.github.autotweaker.core.test

import io.github.autotweaker.api.APP_NAME_LOWERCASE
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.core.PluginLoader
import io.github.autotweaker.core.test.PluginLoaderTest.Companion.CORE_PACKAGE_PATH
import io.github.autotweaker.pluginprobe.PluginImpls
import io.github.autotweaker.pluginprobe.PluginProbe
import io.github.autotweaker.pluginprobe.ProbePlugin
import org.objectweb.asm.ClassReader
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.*
import kotlin.concurrent.thread
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import kotlin.test.*

/**
 * 端到端验证 [PluginLoader] 对插件 jar 的扫描与 apiVersion 兼容性判定。
 *
 * [PluginLoader] 要观察的状态都在进程级：[PluginLoader.load] 扫描不到插件 jar 时会 `exitProcess(1)`，
 * 应用版本取自 [PluginLoader] 所在包的 `Implementation-Version`，`sharedClassLoader` 是进程内单例。
 * 这些状态无法在测试 JVM 内逐场景摆布，因此每个场景都启动一个子 JVM（[PluginProbe]）执行真实的
 * [PluginLoader.load]，测试只负责准备临时插件目录、伪造带 `Implementation-Version` 的清单 jar，
 * 并断言子进程输出。
 *
 * 每个插件 jar 都通过 `META-INF/services/` 声明一个独立的 [ProbePlugin] 实现（见 [PluginImpls]），
 * 因此"被加载的实现类集合"等价于"被接受的插件 jar 集合"。
 */
class PluginLoaderTest {
	
	@Test
	fun `plugin scanning keeps only api compatible jars`() {
		PluginSandbox(RELEASE_VERSION).use { sandbox ->
			sandbox.configJar("01-exact.jar", PluginImpls.Exact::class, id = "exact")
			sandbox.configJar("02-older-patch.jar", PluginImpls.OlderPatch::class, apiVersion = "1.4.1")
			sandbox.configJar("03-prerelease-of-app.jar", PluginImpls.PrereleaseOfApp::class, apiVersion = "1.4.2-rc.1")
			sandbox.configJar("04-newer-patch.jar", PluginImpls.NewerPatch::class, apiVersion = "1.4.3")
			sandbox.configJar("05-newer-minor.jar", PluginImpls.NewerMinor::class, apiVersion = "1.5.0")
			sandbox.configJar("06-newer-major.jar", PluginImpls.NewerMajor::class, apiVersion = "2.0.0")
			sandbox.configJar("07-other-major.jar", PluginImpls.OtherMajor::class, apiVersion = "0.9.9")
			sandbox.configJar("08-no-metadata.jar", PluginImpls.NoMetadata::class, properties = null)
			sandbox.configJar(
				"09-missing-id.jar", PluginImpls.MissingId::class,
				properties = pluginProperties(version = "1.0.0", apiVersion = RELEASE_VERSION)
			)
			sandbox.configJar(
				"10-missing-version.jar", PluginImpls.MissingVersion::class,
				properties = pluginProperties(id = "10-missing-version", apiVersion = RELEASE_VERSION)
			)
			sandbox.configJar(
				"11-missing-api-version.jar", PluginImpls.MissingApiVersion::class,
				properties = pluginProperties(id = "11-missing-api-version", version = "1.0.0")
			)
			sandbox.configJar("12-malformed-version.jar", PluginImpls.MalformedVersion::class, version = "1.0")
			sandbox.configJar(
				"13-malformed-api-version.jar",
				PluginImpls.MalformedApiVersion::class,
				apiVersion = "not-a-semver"
			)
			sandbox.configJar("14-broken-class.jar", PluginImpls.BrokenClass::class, brokenClass = true)
			sandbox.configJar("15-alpha-low.jar", PluginImpls.AlphaLow::class, id = "alpha", version = "1.0.0")
			sandbox.configJar("16-alpha-high.jar", PluginImpls.AlphaHigh::class, id = "alpha", version = "2.0.0")
			sandbox.configJar("17-uppercase.JAR", PluginImpls.UppercaseJar::class)
			// 与 01-exact.jar 同 id 但版本更高，若 CONFIG_PATH 的优先级被破坏就会被它顶掉
			sandbox.installJar("01-exact.jar", PluginImpls.ShadowedByConfig::class, id = "exact", version = "9.9.9")
			sandbox.installJar("02-install-only.jar", PluginImpls.InstallOnly::class)
			
			Files.createDirectories(sandbox.configPlugins.resolve("18-directory.jar"))
			sandbox.configPlugins.resolve("19-not-a-jar.txt").writeText("not a jar")
			sandbox.configPlugins.resolve("20-corrupt.jar").writeText("not a jar either")
			
			sandbox.run().assertLoaded(
				PluginImpls.Exact::class,
				PluginImpls.OlderPatch::class,
				PluginImpls.PrereleaseOfApp::class,
				PluginImpls.AlphaHigh::class,
				PluginImpls.InstallOnly::class,
			)
		}
	}
	
	@Test
	fun `dev application accepts plugins built against the same version triple`() {
		PluginSandbox(DEV_VERSION).use { sandbox ->
			sandbox.configJar("01-same-triple.jar", PluginImpls.DevSameTriple::class)
			sandbox.configJar(
				"02-same-triple-prerelease.jar",
				PluginImpls.DevSameTriplePrerelease::class,
				apiVersion = "1.4.2-beta.1"
			)
			sandbox.configJar("03-older-patch.jar", PluginImpls.DevOlderPatch::class, apiVersion = "1.4.1")
			sandbox.configJar("04-newer-patch.jar", PluginImpls.DevNewerPatch::class, apiVersion = "1.4.3")
			sandbox.configJar("05-other-major.jar", PluginImpls.DevOtherMajor::class, apiVersion = "0.9.9")
			
			sandbox.run().assertLoaded(
				PluginImpls.DevSameTriple::class,
				PluginImpls.DevSameTriplePrerelease::class,
				PluginImpls.DevOlderPatch::class,
			)
		}
	}
	
	@Test
	fun `empty plugin directory terminates the child process`() {
		PluginSandbox(RELEASE_VERSION).use { sandbox ->
			val run = sandbox.run()
			val context = "子进程输出：\n${run.output}"
			
			assertTrue(run.lines.contains(PluginProbe.STARTED_LINE), "子进程应当执行到加载插件，$context")
			assertTrue(run.values(PluginProbe.ERROR_LINE).isEmpty(), "加载插件不应当抛出异常，$context")
			assertFalse(run.lines.contains(PluginProbe.OK_LINE), "扫描不到任何插件 jar 时加载不应当完成，$context")
			assertEquals(1, run.exitCode, "扫描不到插件 jar 时应当 log.error 后 exitProcess(1)，$context")
		}
	}
	
	companion object {
		/** 正式发布版本：三元组相同的 jar 被接受，预发布标识不参与该情形下的判定。 */
		private const val RELEASE_VERSION = "1.4.2"
		
		/** dev 构建的应用版本：三元组相同的 jar 一律被接受，即使它比应用版本更新。 */
		private const val DEV_VERSION = "1.4.2-dev+1789200000.abcdef12"
		
		/** 子进程超时时间，首次启动 JVM 并加载 classpath 通常只需要几秒。 */
		private const val CHILD_TIMEOUT_SECONDS = 120L
		
		/** [PluginLoader] 所在包，其包版本决定应用版本，因此整包 class 都要进伪造清单的 jar。 */
		private const val CORE_PACKAGE_PATH = "io/github/autotweaker/core"
		
		private val JAVA_EXECUTABLE: Path = Path.of(System.getProperty("java.home"), "bin", "java")
		
		/** 子进程运行一次探针：准备临时目录、伪造应用版本、驱动 [PluginProbe] 并回收输出。 */
		private class PluginSandbox(private val applicationVersion: String) : AutoCloseable {
			private val root: Path = Files.createTempDirectory("plugin-loader-test")
			
			/** 作为子进程的 `user.home`，用来把 `CONFIG_PATH` 重定向到临时目录。 */
			private val home: Path = Files.createDirectories(root.resolve("home"))
			
			/** 作为子进程的 `AUTOTWEAKER_INSTALL_PATH`，用来把 `INSTALL_PATH` 重定向到临时目录。 */
			private val install: Path = Files.createDirectories(root.resolve("install"))
			
			/** `CONFIG_PATH/plugins`，插件 id 冲突时优先级最高。 */
			val configPlugins: Path =
				Files.createDirectories(home.resolve(".config").resolve(APP_NAME_LOWERCASE).resolve("plugins"))
			
			/** `INSTALL_PATH/plugins`，插件 id 冲突时优先级最低。 */
			val installPlugins: Path = Files.createDirectories(install.resolve("plugins"))
			
			/** 伪造应用版本的清单 jar，在被调用前不生成。 */
			private val versionJar: Path by lazy { writeVersionJar() }
			
			/** 在 [configPlugins] 中写入一个插件 jar。 */
			fun configJar(
				fileName: String,
				impl: KClass<out ProbePlugin>,
				id: String = fileName.substringBeforeLast('.'),
				version: String = "1.0.0",
				apiVersion: String = applicationVersion,
				properties: String? = pluginProperties(id, version, apiVersion),
				brokenClass: Boolean = false,
			) = writePluginJar(configPlugins.resolve(fileName), properties, impl, brokenClass)
			
			/** 在 [installPlugins] 中写入一个插件 jar。 */
			fun installJar(
				fileName: String,
				impl: KClass<out ProbePlugin>,
				id: String = fileName.substringBeforeLast('.'),
				version: String = "1.0.0",
				apiVersion: String = applicationVersion,
				properties: String? = pluginProperties(id, version, apiVersion),
				brokenClass: Boolean = false,
			) = writePluginJar(installPlugins.resolve(fileName), properties, impl, brokenClass)
			
			/** 运行子进程并断言前置条件成立，返回其输出。 */
			fun run(): ChildRun {
				val process = ProcessBuilder(
					JAVA_EXECUTABLE.toString(),
					"-Duser.home=$home",
					"-cp", childClasspath(),
					PluginProbe::class.java.name,
				).redirectErrorStream(true)
					.directory(File(System.getProperty("user.dir")))
					.apply { environment()["AUTOTWEAKER_INSTALL_PATH"] = install.toString() }
					.start()
				
				val output = StringBuilder()
				val drain = thread(name = "plugin-probe-drain") {
					process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
				}
				if (!process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					process.destroyForcibly()
					fail("子进程在 $CHILD_TIMEOUT_SECONDS 秒内没有结束，已输出：\n$output")
				}
				drain.join()
				
				return ChildRun(process.exitValue(), output.toString()).also(::assertHarness)
			}
			
			override fun close() {
				Files.walk(root).use { paths -> paths.toList().asReversed().forEach { it.deleteIfExists() } }
			}
			
			/**
			 * 先确认子进程确实跑在本用例准备的目录与伪造的应用版本上，
			 * 免得用例自身的构造错误伪装成兼容性判定失败。
			 */
			private fun assertHarness(run: ChildRun) {
				val context = "子进程输出：\n${run.output}"
				
				assertEquals(
					applicationVersion, run.value(PluginProbe.APP_VERSION_LINE),
					"子进程应当读到伪造清单里的应用版本，$context"
				)
				val directories = run.values(PluginProbe.PLUGIN_DIR_LINE)
				assertEquals(
					configPlugins.toString(), directories.firstOrNull(),
					"插件目录应当以 CONFIG_PATH/plugins 开头，$context"
				)
				assertEquals(
					installPlugins.toString(), directories.lastOrNull(),
					"插件目录应当以 INSTALL_PATH/plugins 结尾，$context"
				)
			}
			
			/**
			 * 子进程的 classpath：伪造应用版本的清单 jar 在最前，其后是本测试 JVM 的 classpath。
			 *
			 * Gradle 的测试 worker 未必把应用 classpath 全部放进 `java.class.path`，
			 * 因此再用几个锚点类的 code source 兜底。
			 */
			private fun childClasspath(): String {
				val entries = mutableSetOf(versionJar)
				System.getProperty("java.class.path").orEmpty()
					.split(File.pathSeparatorChar)
					.map { it.trim() }
					.filter { it.isNotEmpty() }
					.map { Path.of(it) }
					.forEach { entries.add(it) }
				listOf(
					PluginLoader::class.java,   // core 主代码
					PluginProbe::class.java,    // 测试代码，探针与全部插件实现都在其中
					SemVer::class.java,         // api 模块
					Unit::class.java,           // kotlin-stdlib
					ClassReader::class.java,    // asm，读取 plugin.properties 时要用
					LoggerFactory::class.java,  // slf4j-api，PluginLoader 的日志要用
				).mapNotNull { codeSourceOf(it) }.forEach { entries.add(it) }
				
				return entries.filter { Files.exists(it) }.joinToString(File.pathSeparator) { it.toString() }
			}
			
			/**
			 * 写入伪造应用版本的清单 jar：包版本取该包第一个被定义的 class 的 code source 清单，
			 * 因此必须把 [CORE_PACKAGE_PATH] 下的全部 class 放进 jar，避免子进程先加载到目录中的同包 class。
			 */
			private fun writeVersionJar(): Path {
				val path = root.resolve("application-version.jar")
				val manifest = Manifest().apply {
					mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
					mainAttributes.putValue("Implementation-Version", applicationVersion)
				}
				JarOutputStream(Files.newOutputStream(path), manifest).use { jar ->
					coreClasses().forEach { (name, bytes) ->
						jar.putNextEntry(JarEntry(name))
						jar.write(bytes)
						jar.closeEntry()
					}
				}
				return path
			}
			
			/** [PluginLoader] 所在包的全部 class，取自其 code source（目录或 jar 均可）。 */
			private fun coreClasses(): List<Pair<String, ByteArray>> {
				val source = codeSourceOf(PluginLoader::class.java)
					?: fail("无法定位 ${PluginLoader::class.java.name} 的 code source")
				
				return if (Files.isDirectory(source)) {
					Files.walk(source.resolve(CORE_PACKAGE_PATH)).use { paths ->
						paths.filter { it.fileName.toString().endsWith(".class") }
							.map {
								val name = source.relativize(it).toString().replace(File.separatorChar, '/')
								name to Files.readAllBytes(it)
							}
							.toList()
					}
				} else {
					JarFile(source.toFile()).use { jar ->
						jar.entries().asSequence()
							.filter { it.name.startsWith("$CORE_PACKAGE_PATH/") && it.name.endsWith(".class") }
							.map { it.name to jar.getInputStream(it).use { stream -> stream.readAllBytes() } }
							.toList()
					}
				}
			}
			
			/**
			 * 写入一个插件 jar：`META-INF/autotweaker/plugin.properties`（[properties] 为 null 时不写）、
			 * `META-INF/services/` 服务声明，以及可选的损坏 class 条目。
			 *
			 * 无论用例期望这个 jar 被接受还是被拒绝，服务声明都照写：一旦判定反了，
			 * 被错误接受的 jar 会多出一个实现，断言才会失败。
			 */
			private fun writePluginJar(
				path: Path,
				properties: String?,
				impl: KClass<out ProbePlugin>,
				brokenClass: Boolean
			) {
				JarOutputStream(Files.newOutputStream(path)).use { jar ->
					if (properties != null) {
						jar.putNextEntry(JarEntry("META-INF/autotweaker/plugin.properties"))
						jar.write(properties.toByteArray())
						jar.closeEntry()
					}
					jar.putNextEntry(JarEntry("META-INF/services/${ProbePlugin::class.java.name}"))
					jar.write("${impl.java.name}\n".toByteArray())
					jar.closeEntry()
					if (brokenClass) {
						jar.putNextEntry(JarEntry("$CORE_PACKAGE_PATH/Broken.class"))
						jar.write(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
						jar.closeEntry()
					}
				}
			}
		}
		
		/** 一次子进程运行的结果，[output] 为合并后的 stdout 与 stderr。 */
		private class ChildRun(val exitCode: Int, val output: String) {
			val lines: List<String> = output.lines()
			
			/** 所有以 [prefix] 开头的行的剩余部分，顺序与子进程输出一致。 */
			fun values(prefix: String): List<String> =
				lines.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
			
			fun value(prefix: String): String? = values(prefix).firstOrNull()
		}
		
		/**
		 * 断言子进程恰好加载了 [expected] 中的插件实现，
		 * 其余插件 jar 应当因 apiVersion 不兼容、元数据缺失或无法读取而被跳过。
		 */
		private fun ChildRun.assertLoaded(vararg expected: KClass<out ProbePlugin>) {
			val context = "子进程输出：\n$output"
			
			assertEquals(0, exitCode, "插件加载应当正常结束，$context")
			assertTrue(lines.contains(PluginProbe.OK_LINE), "插件加载应当执行完成，$context")
			assertEquals(
				expected.map { it.java.simpleName }.toSet(),
				values(PluginProbe.PLUGIN_LINE).toSet(),
				"被加载的插件应当恰好是 apiVersion 兼容的插件 jar，$context",
			)
		}
		
		/** 组装 `plugin.properties` 的内容，参数为 null 时省略对应条目。 */
		private fun pluginProperties(id: String? = null, version: String? = null, apiVersion: String? = null): String =
			listOfNotNull(
				id?.let { "id=$it" },
				version?.let { "version=$it" },
				apiVersion?.let { "apiVersion=$it" },
			).joinToString(separator = "\n", postfix = "\n")
		
		/** 类的 code source，即其 class 文件所在的目录或 jar。 */
		private fun codeSourceOf(type: Class<*>): Path? =
			type.protectionDomain?.codeSource?.location?.let { Path.of(it.toURI()) }
	}
}
