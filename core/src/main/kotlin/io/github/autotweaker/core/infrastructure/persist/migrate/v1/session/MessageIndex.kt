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

package io.github.autotweaker.core.infrastructure.persist.migrate.v1.session

import io.github.autotweaker.api.discard
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.cjk.CJKAnalyzer
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.time.Instant

object MessageIndex {
	private const val FIELD_ID = "id"
	private const val FIELD_TYPE = "type"
	private const val FIELD_TIMESTAMP = "timestamp"
	private const val FIELD_SEARCH_TEXT = "searchText"
	
	private val INDEX_PATH: Path = DB_PATH.resolve("message_search")
	private val directory: Directory by lazy {
		Files.createDirectories(INDEX_PATH)
		FSDirectory.open(INDEX_PATH)
	}
	private val analyzer by lazy { CJKAnalyzer() }
	private val writer by lazy { IndexWriter(directory, IndexWriterConfig(analyzer)) }
	
	suspend fun upsert(id: UUID, type: String, timestamp: Instant, searchText: String?) =
		withContext(Dispatchers.IO) {
			val fields = arrayListOf(
				StringField(FIELD_ID, id.toString(), Field.Store.YES),
				StringField(FIELD_TYPE, type, Field.Store.NO),
				LongPoint(FIELD_TIMESTAMP, timestamp.toEpochMilliseconds()),
			)
			if (searchText != null)
				fields.add(TextField(FIELD_SEARCH_TEXT, searchText, Field.Store.NO))
			writer.updateDocument(Term(FIELD_ID, id.toString()), fields)
		}.discard()
	
	suspend fun close() = withContext(Dispatchers.IO) {
		writer.close()
		analyzer.close()
		directory.close()
	}
}
