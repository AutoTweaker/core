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
			val valueType: ValueType
		) {
			sealed class ValueType {
				data class StringValue(val enum: List<String>? = null) : ValueType()
				data class NumberValue(val enum: List<Double>? = null) : ValueType()
				data class IntegerValue(val enum: List<Int>? = null) : ValueType()
				data object BooleanValue : ValueType()
				data class ArrayValue(val items: ValueType) : ValueType()
				data class ObjectValue(val properties: Map<String, ValueType>) : ValueType()
			}
		}
	}
	
	suspend fun execute(
		input: I
	): O
}
