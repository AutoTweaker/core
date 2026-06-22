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

interface Loggable
interface Traceable
interface JsonStorable
interface Settable
interface I18nable

inline val Loggable.log: Logger get() = LoggerFactory.getLogger(this::class.java)

val Traceable.trace: TraceRecorder get() = services.trace(this::class)

val JsonStorable.store: JsonStore get() = services.store(this::class)

@Suppress("UnusedReceiverParameter")
val Settable.setting: SettingService get() = services.setting

@Suppress("UnusedReceiverParameter")
val I18nable.i18n: I18nService get() = services.i18n


internal inline val services get() = ServiceRegistry.servicesOrError()
