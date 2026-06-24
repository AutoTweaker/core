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

import io.github.autotweaker.api.config.JsonStore
import io.github.autotweaker.api.config.SettingService
import io.github.autotweaker.api.i18n.I18nService
import io.github.autotweaker.api.trace.TraceRecorder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 允许对象通过 [log] 获取 [Logger]
 *
 * 实现此接口即可直接 `log.info("Hello World")` 而无需 [LoggerFactory.getLogger]
 */
interface Loggable

/**
 * 允许对象通过 [trace] 获取 [TraceRecorder]
 */
interface Traceable

/**
 * 允许对象通过 [store] 获取 [JsonStore]
 */
interface JsonStorable

/**
 * 允许对象通过 [setting] 获取 [SettingService]
 */
interface Settable

/**
 * 允许对象通过 [i18n] 获取 [I18nService]
 */
interface I18nable

inline val Loggable.log: Logger get() = LoggerFactory.getLogger(this::class.java)

/**
 * 获取 AutoTweaker 提供的 [TraceRecorder]，自身作为 `origin`。
 */
val Traceable.trace: TraceRecorder get() = services.trace(this::class)

/**
 * 获取 AutoTweaker 提供的 [JsonStore]。
 * 同一类获取到的 [JsonStore] 始终相同，不同类获取到的 [JsonStore] 相互隔离。
 */
val JsonStorable.store: JsonStore get() = services.store(this::class)

/**
 * 获取 AutoTweaker 提供的 [SettingService]
 */
@Suppress("UnusedReceiverParameter")
val Settable.setting: SettingService get() = services.setting

/**
 * 获取 AutoTweaker 提供的 [I18nService]
 */
@Suppress("UnusedReceiverParameter")
val I18nable.i18n: I18nService get() = services.i18n


internal inline val services get() = ServiceRegistry.servicesOrError()
