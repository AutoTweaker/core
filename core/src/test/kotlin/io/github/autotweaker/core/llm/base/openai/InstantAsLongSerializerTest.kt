package io.github.autotweaker.core.llm.base.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class InstantAsLongSerializerTest {
    
    private val json = Json { encodeDefaults = true }
    
    @Serializable
    data class Wrapper(
        @Serializable(with = InstantAsLongSerializer::class)
        val instant: Instant
    )
    
    @Test
    fun `serialize Instant to Unix epoch seconds`() {
        val t = Instant.fromEpochSeconds(1715678901)
        val wrapper = Wrapper(t)
        val encoded = json.encodeToString(wrapper)
        
        assertEquals("""{"instant":1715678901}""", encoded)
    }
    
    @Test
    fun `deserialize Unix epoch seconds to Instant`() {
        val jsonStr = """{"instant":1715678901}"""
        val wrapper = json.decodeFromString<Wrapper>(jsonStr)
        
        assertEquals(Instant.fromEpochSeconds(1715678901), wrapper.instant)
    }
    
    @Test
    fun `deserialize epoch zero`() {
        val jsonStr = """{"instant":0}"""
        val wrapper = json.decodeFromString<Wrapper>(jsonStr)
        
        assertEquals(Instant.fromEpochSeconds(0), wrapper.instant)
    }
}
