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

package io.github.autotweaker.core.infrastructure.persist.db.objectstore

import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.storage.ObjectStorage
import io.github.autotweaker.api.types.Sha256
import io.github.autotweaker.core.infrastructure.persist.db.transaction
import io.github.autotweaker.core.infrastructure.persist.store.DatabaseStore
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

object ObjectStorageImpl : ObjectStorage, Loggable {
	private lateinit var db: Database
	
	suspend fun init(databaseStore: DatabaseStore) {
		db = databaseStore.connect("Objects")
		db.transaction { SchemaUtils.create(ObjectStoreTable) }
		log.info("Initialized ObjectStorage")
	}
	
	override suspend fun put(bytes: ByteArray): Sha256 =
		Sha256.hash(bytes).also { sha256 ->
			db.transaction {
				ObjectStoreTable.insertIgnore {
					it[hash] = sha256.bytes
					it[content] = ExposedBlob(bytes)
				}
			}
		}
	
	
	override suspend fun get(sha256: Sha256): ByteArray? = db.transaction {
		ObjectStoreTable.selectAll().where { ObjectStoreTable.hash eq sha256.bytes }
			.firstOrNull()?.get(ObjectStoreTable.content)?.bytes
	}
	
	// TODO 目前只能增不能删，可能未来需要考虑清理机制
}
