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

package io.github.autotweaker.core.infrastructure.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds

object HttpFetcher {
	private val log = LoggerFactory.getLogger(HttpFetcher::class.java)
	
	private val baseUrl: String
		get() = System.getenv("AUTOTWEAKER_WEBSITE_URL") ?: error("AUTOTWEAKER_WEBSITE_URL not set")
	
	private val client: HttpClient? by lazy {
		if (!baseUrl.startsWith("http")) return@lazy null
		val proxyUrl = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")
		HttpClient(CIO) {
			if (proxyUrl != null) {
				val uri = URI(proxyUrl)
				engine {
					proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
				}
			}
			install(HttpTimeout) {
				connectTimeoutMillis = 5_000
				requestTimeoutMillis = 15_000
			}
		}
	}
	
	suspend fun fetch(path: String, retries: Int = 3): String {
		val resolvedUrl = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
		var lastException: Exception? = null
		repeat(retries) { attempt ->
			try {
				val httpClient = client
				return httpClient?.get(resolvedUrl)?.body() ?: Path.of(baseUrl, path).readText()
			} catch (e: Exception) {
				lastException = e
				if (attempt < retries - 1) {
					val backoff = (1L shl (attempt + 1)) * 1000L
					log.warn(
						"HttpFetcher retry  attempt={}/{}  path={}  reason={}", attempt + 1, retries, path, e.message
					)
					delay(backoff.milliseconds)
				}
			}
		}
		throw lastException!!
	}
}