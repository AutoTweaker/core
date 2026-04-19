@file:JvmName("Url")

package io.github.autotweaker.core

import java.net.URI

@JvmInline
value class Url private constructor(val value: String) {
	companion object {
		operator fun invoke(raw: String): Url {
			val trimmed = raw.trim().trimEnd('/')
			require(trimmed.isNotBlank()) { "Base URL must not be blank" }
			runCatching { URI(trimmed) }.getOrNull()
				?.takeIf { it.isAbsolute && it.scheme in listOf("http", "https") }
				?: throw IllegalArgumentException("Invalid base URL: $trimmed")
			return Url(trimmed)
		}
	}
}
