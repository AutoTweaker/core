@file:JvmName("Unicode")

package io.github.autotweaker.core

@Suppress("unused")
@JvmInline
value class Unicode(val value: String) {
	init {
		require(isValid(value)) { "Invalid Unicode escape sequence: $value" }
	}
	
	/** 解码为对应的字符 */
	fun toChar(): Char = value.substring(2).toInt(16).toChar()
	
	/** 获取 Unicode 码点值 */
	fun codePoint(): Int = value.substring(2).toInt(16)
	
	companion object {
		/** 从字符创建 Unicode 转义序列 */
		fun fromChar(char: Char): Unicode =
			Unicode("\\u${char.code.toString(16).padStart(4, '0')}")
		
		/** 从码点创建 Unicode 转义序列 */
		fun fromCodePoint(codePoint: Int): Unicode {
			require(codePoint in 0..0xFFFF) { "Code point out of range for \\uXXXX format: $codePoint" }
			return Unicode("\\u${codePoint.toString(16).padStart(4, '0')}")
		}
		
		fun isValid(input: String): Boolean {
			if (input.length != 6) return false
			if (!input.startsWith("\\u")) return false
			return input.substring(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
		}
	}
}
