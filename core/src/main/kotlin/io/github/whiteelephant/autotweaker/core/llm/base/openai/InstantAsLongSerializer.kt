package io.github.whiteelephant.autotweaker.core.llm.base.openai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * 序列化器：将 [Instant] 与 Unix 秒时间戳（Long）互转。
 * OpenAI 兼容 API 的 `created` 字段使用 Unix 秒。
 */
object InstantAsLongSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.epochSeconds)
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.fromEpochSeconds(decoder.decodeLong())
    }
}
