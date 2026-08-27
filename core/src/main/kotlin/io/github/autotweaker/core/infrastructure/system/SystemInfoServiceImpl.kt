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

package io.github.autotweaker.core.infrastructure.system

import com.sun.management.OperatingSystemMXBean
import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.base.getOrDefault
import io.github.autotweaker.api.base.recoverException
import io.github.autotweaker.core.domain.port.SystemInfo
import io.github.autotweaker.core.domain.port.SystemInfoService
import java.io.IOException
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

object SystemInfoServiceImpl : SystemInfoService, Loggable, Traceable {
	private const val OS_RELEASE_PATH = "/etc/os-release"
	private const val HOSTNAME_PATH = "/proc/sys/kernel/hostname"
	private val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
	private val osName = System.getProperty("os.name").orEmpty()
	
	override fun get(): SystemInfo = SystemInfo(
		osName = osName,
		hostname = readHostname(),
		distribution = readDistribution(),
		kernelVersion = System.getProperty("os.version").orEmpty(),
		cpuArch = System.getProperty("os.arch").orEmpty(),
		user = System.getProperty("user.name").orEmpty(),
		cpuCoreCount = Runtime.getRuntime().availableProcessors(),
		totalMemory = osBean.totalMemorySize,
	)
	
	private fun readHostname(): String = trace.catching {
		Files.readString(Path.of(HOSTNAME_PATH)).trim().orNull()
			?: InetAddress.getLocalHost().hostName
	}.getOrDefault("")
	
	private fun readDistribution(): String = trace.catching {
		Files.readAllLines(Path.of(OS_RELEASE_PATH))
			.firstOrNull { it.startsWith("PRETTY_NAME=") }
			?.removePrefix("PRETTY_NAME=")
			?.trim('"')
			?.orNull() ?: osName
	}.recoverException { _: IOException -> osName }
		.onFailure { log.warn("Failed to read OS release  path={}  reason={}", OS_RELEASE_PATH, it.message) }
		.getOrDefault(osName)
}
