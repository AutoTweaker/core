package io.github.whiteelephant.autotweaker.core.data.database.store

interface DatabaseStore {
    fun connect(dbName: String)
}
