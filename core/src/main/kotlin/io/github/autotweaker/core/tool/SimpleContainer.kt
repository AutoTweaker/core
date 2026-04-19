package io.github.autotweaker.core.tool

import kotlin.reflect.KClass

class SimpleContainer : DependencyProvider {
	private val services = mutableMapOf<KClass<*>, Any>()
	
	@Suppress("unused")
	fun <T : Any> register(serviceClass: KClass<T>, instance: T) {
		services[serviceClass] = instance
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <T : Any> get(serviceClass: KClass<T>): T {
		return services[serviceClass] as? T
			?: throw NoSuchElementException("Service ${serviceClass.simpleName} not found.")
	}
}
