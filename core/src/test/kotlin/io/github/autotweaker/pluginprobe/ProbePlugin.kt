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

/**
 * 测试用插件接口，插件 jar 通过 `META-INF/services/<本接口全名>` 声明实现类。
 *
 * 实现类位于测试 classpath（见 [PluginImpls]），因此插件 jar 中不需要真实的 class 文件。
 */
interface ProbePlugin

/**
 * 测试用插件实现，每个插件 jar 声明一个独立的实现类。
 *
 * 一个子进程中只跑一个场景，实现类与插件 jar 一一对应，
 * 因此断言被加载的实现类集合就等价于断言"哪些插件 jar 被 `PluginLoader` 接受"。
 */
object PluginImpls {
	class Exact : ProbePlugin
	class OlderPatch : ProbePlugin
	class PrereleaseOfApp : ProbePlugin
	class NewerPatch : ProbePlugin
	class NewerMinor : ProbePlugin
	class NewerMajor : ProbePlugin
	class OtherMajor : ProbePlugin
	class NoMetadata : ProbePlugin
	class MissingId : ProbePlugin
	class MissingVersion : ProbePlugin
	class MissingApiVersion : ProbePlugin
	class MalformedVersion : ProbePlugin
	class MalformedApiVersion : ProbePlugin
	class BrokenClass : ProbePlugin
	class AlphaLow : ProbePlugin
	class AlphaHigh : ProbePlugin
	class UppercaseJar : ProbePlugin
	class ShadowedByConfig : ProbePlugin
	class InstallOnly : ProbePlugin
	class DevSameTriple : ProbePlugin
	class DevSameTriplePrerelease : ProbePlugin
	class DevOlderPatch : ProbePlugin
	class DevNewerPatch : ProbePlugin
	class DevOtherMajor : ProbePlugin
}
