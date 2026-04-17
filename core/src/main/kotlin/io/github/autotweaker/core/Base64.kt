@file:JvmName("Base64")

package io.github.autotweaker.core

import kotlin.io.encoding.Base64 as KBase64
import kotlin.io.encoding.ExperimentalEncodingApi

@JvmInline
value class Base64(val value: String) {
    init {
        require(isValid(value)) { "Invalid Base64 string" }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(): ByteArray = KBase64.decode(value)

    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun encode(bytes: ByteArray): Base64 = Base64(KBase64.encode(bytes))

        fun isValid(input: String): Boolean {
            if (input.length % 4 != 0) return false
            return input.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        }
    }
}
