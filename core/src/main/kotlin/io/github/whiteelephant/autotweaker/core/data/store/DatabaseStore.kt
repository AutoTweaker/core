package io.github.whiteelephant.autotweaker.core.data.store

interface DatabaseStore {
    fun connect(dbName: String)
}
