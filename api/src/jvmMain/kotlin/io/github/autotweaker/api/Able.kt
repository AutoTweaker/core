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

package io.github.autotweaker.api

import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.storage.JsonStore
import io.github.autotweaker.api.storage.ObjectStorage
import io.github.autotweaker.api.trace.TraceRecorder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 允许对象通过 [log] 获取 [Logger]，AutoTweaker 使用 logback 作为实现，插件仅需依赖 slf4j
 *
 * 实现此接口即可直接 `log.info("Hello World")` 而无需 [LoggerFactory.getLogger]
 */
interface Loggable

/**
 * 允许对象通过 [trace] 获取 [TraceRecorder]
 *
 * @see TraceRecorder
 */
interface Traceable

/**
 * 允许对象通过 [store] 获取 [JsonStore]
 *
 * @see JsonStore
 */
interface JsonStorable

/**
 * 允许对象通过 [objects] 获取 [ObjectStorage]
 *
 * @see ObjectStorage
 */
interface ObjectStorable

/**
 * 允许对象通过 [setting] 获取 [SettingService]
 *
 * @see SettingService
 */
interface Settable

/**
 * 允许对象通过 [i18n] 获取 [I18nService]
 *
 * @see I18nService
 */
interface I18nable

/**
 * 获取 [Logger]，请遵循日志规范，不要污染程序日志。
 *
 * 直接在实现了 [Loggable] 的类 / 对象内部的任何地方使用 `log` 即可。
 *
 * 日志规范：
 * - 用语：英语，过去时或现在完成时，不要使用中文以及中文标点，用词："initialized"、"started"、"failed to shutdown"，通常动词在前，如 "Created session" "Completed agent shutdown"
 * - 格式：首字母大写，不加句号。正文仅使用字母、数字、空格、短横线，不要使用点，字母之间使用一个空格
 * - 变量：正文 + 双空格 + 变量，`key=value`，键值中间无空格，变量名使用 kotlin 风格，空格分隔多个字段，无逗号，使用 `{}` 而非字符串模板，不要把大段数据输出到日志，请使用 [TraceRecorder]
 * - 标识：应当包含必要的实例标识和足够的上下文，但类名会由框架自动记录，可以省略自我介绍
 * - 异常：通常不使用 INFO 级别记录异常，ERROR 级别异常传异常对象，WARN 级别不应当传递异常对象，但可尽量传递 `e.message`
 */
inline val Loggable.log: Logger get() = LoggerFactory.getLogger(this::class.java)

/**
 * 获取 AutoTweaker 提供的 [TraceRecorder]，自身作为 `origin`。
 *
 * 直接在实现了 [Traceable] 的类 / 对象内部的任何地方使用 `trace` 即可。
 *
 * AutoTweaker 内部会缓存已创建的 TraceRecorder 对象。
 */
inline val Traceable.trace: TraceRecorder get() = services.trace(this::class)

/**
 * 获取 AutoTweaker 提供的 [JsonStore]。
 * 同一类获取到的 [JsonStore] 始终相同，不同类获取到的 [JsonStore] 相互隔离。
 *
 * AutoTweaker 内部会缓存已创建的 JsonStore 对象。
 *
 * 直接在实现了 [JsonStorable] 的类 / 对象内部的任何地方使用 `store` 即可。
 */
inline val JsonStorable.store: JsonStore get() = services.store(this::class)

/**
 * 获取 AutoTweaker 提供的 [ObjectStorage]。
 *
 * 直接在实现了 [ObjectStorable] 的类 / 对象内部的任何地方使用 `objects` 即可。
 */
@Suppress("UnusedReceiverParameter")
inline val ObjectStorable.objects: ObjectStorage get() = services.objects

/**
 * 获取 AutoTweaker 提供的 [SettingService]。
 *
 * 直接在实现了 [Settable] 的类 / 对象内部的任何地方使用 `setting` 即可。
 */
@Suppress("UnusedReceiverParameter")
inline val Settable.setting: SettingService get() = services.setting

/**
 * 获取 AutoTweaker 提供的 [I18nService]。
 *
 * 直接在实现了 [I18nable] 的类 / 对象内部的任何地方使用 `i18n` 即可。
 */
@Suppress("UnusedReceiverParameter")
inline val I18nable.i18n: I18nService get() = services.i18n

@PublishedApi
internal inline val services get() = ServiceRegistry.servicesOrError()
