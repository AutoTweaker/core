package io.github.autotweaker.core.tool

import kotlin.reflect.KClass

interface DependencyProvider {
	fun <T : Any> get(serviceClass: KClass<T>): T
}

inline fun <reified T : Any> DependencyProvider.get(): T = get(T::class)
