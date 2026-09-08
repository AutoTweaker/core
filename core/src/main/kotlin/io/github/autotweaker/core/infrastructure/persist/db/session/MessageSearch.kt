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

package io.github.autotweaker.core.infrastructure.persist.db.session

import io.github.autotweaker.api.discard
import io.github.autotweaker.api.types.agent.AgentMessageType
import io.github.autotweaker.core.infrastructure.persist.db.base.DB_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.lucene.analysis.cjk.CJKAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.time.Instant

object MessageSearch {
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
	private val searcherManager by lazy { SearcherManager(writer, SearcherFactory()) }
	
	suspend fun upsert(id: UUID, type: AgentMessageType, timestamp: Instant, searchText: String?) =
		withContext(Dispatchers.IO) {
			val fields = arrayListOf(
				StringField(FIELD_ID, id.toString(), Field.Store.YES),
				StringField(FIELD_TYPE, type.name, Field.Store.NO),
				LongPoint(FIELD_TIMESTAMP, timestamp.toEpochMilliseconds()),
			)
			if (searchText != null)
				fields.add(TextField(FIELD_SEARCH_TEXT, searchText, Field.Store.NO))
			writer.updateDocument(Term(FIELD_ID, id.toString()), fields)
		}.discard()
	
	suspend fun delete(ids: Collection<UUID>) {
		if (ids.isEmpty()) return
		withContext(Dispatchers.IO) {
			writer.deleteDocuments(*ids.mapTo(arrayListOf()) {
				Term(FIELD_ID, it.toString())
			}.toTypedArray())
		}
	}
	
	suspend fun search(
		query: String,
		type: AgentMessageType?,
		from: Instant?,
		to: Instant?,
	): Set<UUID> = withContext(Dispatchers.IO) {
		searcherManager.maybeRefresh()
		val searcher = searcherManager.acquire()
		try {
			searchIds(searcher, query, type, from, to)
		} finally {
			searcherManager.release(searcher)
		}
	}
	
	suspend fun close() = withContext(Dispatchers.IO) {
		searcherManager.close()
		writer.close()
		analyzer.close()
		directory.close()
	}
	
	private fun searchIds(
		searcher: IndexSearcher,
		query: String,
		type: AgentMessageType?,
		from: Instant?,
		to: Instant?,
	): Set<UUID> {
		val terms = tokenize(query)
		if (terms.isEmpty()) return emptySet()
		val builder = BooleanQuery.Builder()
		terms.forEach { term ->
			builder.add(TermQuery(Term(FIELD_SEARCH_TEXT, term)), BooleanClause.Occur.MUST)
		}
		type?.let {
			builder.add(TermQuery(Term(FIELD_TYPE, it.name)), BooleanClause.Occur.FILTER)
		}
		from?.let {
			builder.add(
				LongPoint.newRangeQuery(FIELD_TIMESTAMP, it.toEpochMilliseconds(), Long.MAX_VALUE),
				BooleanClause.Occur.FILTER
			)
		}
		to?.let {
			builder.add(
				LongPoint.newRangeQuery(FIELD_TIMESTAMP, Long.MIN_VALUE, it.toEpochMilliseconds()),
				BooleanClause.Occur.FILTER
			)
		}
		val storedFields = searcher.storedFields()
		return searcher.search(builder.build(), Int.MAX_VALUE).scoreDocs
			.mapTo(mutableSetOf()) {
				UUID.fromString(storedFields.document(it.doc).get(FIELD_ID))
			}
	}
	
	private fun tokenize(query: String): List<String> {
		val terms = mutableListOf<String>()
		analyzer.tokenStream(FIELD_SEARCH_TEXT, StringReader(query)).use { stream ->
			val term = stream.addAttribute(CharTermAttribute::class.java)
			stream.reset()
			while (stream.incrementToken()) terms.add(term.toString())
		}
		return terms
	}
}
