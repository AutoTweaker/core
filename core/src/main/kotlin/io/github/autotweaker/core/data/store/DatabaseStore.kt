package io.github.autotweaker.core.data.store

interface DatabaseStore {
	fun connect(dbName: String)
}
