package io.github.whiteelephant.autotweaker.core.data.settings

import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.*

import io.github.whiteelephant.autotweaker.core.Price
import kotlinx.serialization.Serializable

object ConfigTable : Table("core_settings") {
    val keyName = varchar("key_name", 255)
    val valJson = text("val_json")

    override val primaryKey = PrimaryKey(keyName)
}

data class SettingItem(
    val key: SettingKey,
    val value: Value
) {
    @Serializable
    sealed class Value {
        //基本类型
        @Serializable
        data class ValByte(
            val value: Byte
        ) : Value()

        @Serializable
        data class ValShort(
            val value: Short
        ) : Value()

        @Serializable
        data class ValInt(
            val value: Int
        ) : Value()

        @Serializable
        data class ValLong(
            val value: Long
        ) : Value()

        @Serializable
        data class ValFloat(
            val value: Float
        ) : Value()

        @Serializable
        data class ValDouble(
            val value: Double
        ) : Value()

        @Serializable
        data class ValBoolean(
            val value: Boolean
        ) : Value()

        @Serializable
        data class ValChar(
            val value: Char
        ) : Value()

        @Serializable
        data class ValString(
            val value: String
        ) : Value()

        //数据类
        @Serializable
        data class Providers(
            val providers: List<Provider>
        ) {
            @Serializable
            data class Provider(
                val name: String,
                val providerType: String,
                val apiKey: String,
                val baseUrl: String,
                val models: List<Model>,
                val errorHandlingRules: List<ErrorHandlingRule>
            ) {
                @Serializable
                data class ErrorHandlingRule(
                    val statusCode: Int,
                    val strategy: RecoveryStrategy
                ) {
                    @Serializable
                    enum class RecoveryStrategy {
                        RETRY,
                        FALLBACK,
                        CONTEXT_FALLBACK,
                        PROVIDER_FALLBACK,
                    }
                }

                @Serializable
                data class Model(
                    val name: String,

                    val contextWindow: Int,
                    val maxOutputTokens: Int,
                    val price: TokenPrice,

                    val supportsStreaming: Boolean,
                    val supportsToolCalls: Boolean,
                    val supportsReasoning: Boolean,
                    val supportsImage: Boolean,
                ) {
                    @Serializable
                    data class TokenPrice(
                        val inputPrice: List<PriceTier>,
                        val outputPrice: List<PriceTier>,
                    ) {
                        @Serializable
                        data class PriceTier(
                            val fromTokens: Int,
                            val toTokens: Int? = null,
                            val price: Price,
                            val cachedPrice: Price? = null
                        )
                    }

                }
            }
        }
    }
}
