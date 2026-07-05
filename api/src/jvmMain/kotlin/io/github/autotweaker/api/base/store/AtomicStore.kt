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

package io.github.autotweaker.api.base.store

import io.github.autotweaker.api.IO
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.scope
import io.github.autotweaker.api.store
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * 使用 [AtomicReference] 为缓存，[io.github.autotweaker.api.storage.JsonStore] 为持久化服务的存储基类。
 *
 * [AtomicStore] 的优点是无锁，缺点是 [update] 块内不能挂起、不能有副作用。
 *
 * [V] 应为一个不可变的类型，例如一个 **不** 包含 `var` 的数据类，或 [java.util.UUID] 这类不可直接变更值的类型，反例：[MutableCollection]。如果需要包装可变类型，请使用 [MutableStore]。
 *
 * 由于无锁，为了避免并发场景下可能的缓存与硬盘不同步，缓存会在后台异步保存，每半秒检查一次是否需要保存。
 *
 * 请确保在程序关闭前调用 [shutdown] 方法，可以注册一个 [io.github.autotweaker.api.hook.ShutdownHook] 来调用 [shutdown]。
 */
abstract class AtomicStore<V> : StoreBase<V>(), Loggable {
	@Volatile
	private var initialized = false
	private val accessor by lazy { JsonStoreAccessor(store, serializer, ::default).also { initialized = true } }
	private val cache: AtomicReference<V> by lazy { AtomicReference(accessor.initial) }
	private val dirty = AtomicBoolean(false)
	private val scope = scope(IO)
	
	init {
		scope.launch {
			while (true) {
				delay(500.milliseconds)
				if (dirty.compareAndSet(true, false)) {
					accessor.save(cache.get())
				}
			}
		}
	}
	
	/**
	 * 取消用于保存的协程，然后检查是否需要保存，若需要，执行最后一次保存。
	 */
	fun shutdown() {
		scope.cancel()
		if (initialized && dirty.get()) accessor.save(cache.get())
	}
	
	/**
	 * 从内存获取当前值。
	 */
	protected fun get(): V = cache.get()
	
	/**
	 * 更新内存中的值，会自动保存到数据库。
	 */
	protected fun set(value: V) {
		cache.set(value)
		dirty.set(true)
	}
	
	/**
	 * 通过 [java.util.concurrent.atomic.AtomicReference.updateAndGet] 更新内存中的值，[transform] 不能有任何副作用，哪怕是日志。
	 *
	 * @param transform 用于计算新值的 lambda，可能被多次执行，即使只有一次 update 调用。
	 */
	protected fun update(transform: (V) -> V) {
		cache.updateAndGet(transform)
		dirty.set(true)
	}
}
