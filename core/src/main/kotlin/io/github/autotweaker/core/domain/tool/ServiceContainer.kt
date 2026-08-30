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

package io.github.autotweaker.core.domain.tool

import io.github.autotweaker.api.base.guava.ClassToInstanceMap
import kotlin.reflect.KClass

class ServiceContainer : DependencyProvider {
	val services = ClassToInstanceMap<Any>()
	
	inline fun <reified T : Any> register(instance: T) = also {
		services.putInstance(T::class.java, instance)
	}
	
	override fun <T : Any> get(clazz: KClass<T>): T =
		services.getInstance(clazz.java)
			?: throw NoSuchElementException("Service ${clazz.simpleName} not found.")
}
