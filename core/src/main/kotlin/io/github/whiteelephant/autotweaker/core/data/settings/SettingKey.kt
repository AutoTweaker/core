package io.github.whiteelephant.autotweaker.core.data.settings

@JvmInline
value class SettingKey private constructor(val value: String) {
    companion object {
        private val SEGMENT_PATTERN = Regex("^[a-z0-9]{2,}$")

        operator fun invoke(raw: String): SettingKey {
            require(raw.isNotBlank()) { "SettingKey must not be blank" }
            require(!raw.startsWith('.')) { "SettingKey must not start with '.'" }
            require(!raw.endsWith('.')) { "SettingKey must not end with '.'" }
            val segments = raw.split('.')
            require(segments.all { SEGMENT_PATTERN.matches(it) }) {
                "Each segment must be 2+ lowercase letters or digits, got: $raw"
            }
            return SettingKey(raw)
        }
    }
}
