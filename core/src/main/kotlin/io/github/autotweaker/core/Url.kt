@file:JvmName("Url")

package io.github.autotweaker.core

import kotlinx.serialization.Serializable
import java.net.URI

@JvmInline
@Serializable
value class Url private constructor(val value: String) {
	companion object {
		operator fun invoke(raw: String): Url {
			val trimmed = raw.trim().trimEnd('/')
			require(trimmed.isNotBlank()) { "URL must not be blank" }
			runCatching { URI(trimmed) }.getOrNull()
				?.takeIf { it.isAbsolute && it.scheme in listOf("http", "https") }
				?: throw IllegalArgumentException("Invalid URL: $trimmed")
			return Url(trimmed)
		}
	}
}
