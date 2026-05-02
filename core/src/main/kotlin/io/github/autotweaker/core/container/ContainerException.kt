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

package io.github.autotweaker.core.container

class ContainerAlreadyRunningException(containerId: String) :
	IllegalStateException("A container is already running (id: $containerId). Only one container is allowed at a time.")

class NoContainerRunningException :
	IllegalStateException("No container is running. Start a container first.")

class ContainerOperationException(message: String, cause: Throwable? = null) :
	RuntimeException(message, cause)
