package io.github.autotweaker.core.tool

interface Tool<in I : ToolInput, out O : ToolOutput> {
	val name: String
	val description: String
	val functions: List<Function>
	
	data class Function(
		val name: String,
		val description: String,
		val parameters: Map<String, Property>,
	) {
		data class Property(
			val description: String,
			val required: Boolean,
			val value: Value
		) {
			sealed class Value {
				data class StringValue(val enum: List<String>? = null) : Value()
				data class NumberValue(val enum: List<Double>? = null) : Value()
				data class IntegerValue(val enum: List<Int>? = null) : Value()
				data object BooleanValue : Value()
				data class ArrayValue(val items: Value) : Value()
				data class ObjectValue(
					val properties: Map<String, Property>,
				) : Value()
			}
		}
	}
	
	suspend fun execute(
		input: I
	): O
}
