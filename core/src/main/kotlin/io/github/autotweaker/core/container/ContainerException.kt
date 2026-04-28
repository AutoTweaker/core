package io.github.autotweaker.core.container

class ContainerAlreadyRunningException(containerId: String) :
	IllegalStateException("A container is already running (id: $containerId). Only one container is allowed at a time.")

class NoContainerRunningException :
	IllegalStateException("No container is running. Start a container first.")

class ContainerOperationException(message: String, cause: Throwable? = null) :
	RuntimeException(message, cause)
