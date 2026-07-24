/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.autotweaker.toolgen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.nio.file.Path

internal class ArgsCodeGen(
	private val meta: ToolMeta,
	private val argsPackage: String,
	private val toolPackage: String,
) {
	private val namePascal = meta.name.toPascalCase()
	private val metaType = ClassName(toolPackage, "${namePascal}MetaDescriptions")
	private val argsType = ClassName(argsPackage, "${namePascal}Args")
	private val runtimeMeta = ClassName("io.github.autotweaker.api.types.tool", "ToolMeta")
	private val runtimeFunction = runtimeMeta.nestedClass("Function")
	private val runtimeProp = runtimeMeta.nestedClass("Prop")
	private val runtimeType = ClassName("io.github.autotweaker.api.types.tool", "ToolMeta", "Type")
	
	private val fileSuppress by lazy {
		AnnotationSpec.builder(Suppress::class.asTypeName())
			.addMember("%S, %S, %S", "RedundantVisibilityModifier", "RemoveRedundantBackticks", "unused")
			.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).build()
	}
	
	operator fun invoke(outputDir: Path) {
		generateArgs(outputDir)
		generateDescriptions(outputDir)
		generateMetaBuilder(outputDir)
	}
	
	private fun generateArgs(outputDir: Path) {
		val sealed =
			TypeSpec.classBuilder("${namePascal}Args").addModifiers(KModifier.SEALED).addAnnotation(Serializable)
				.addSuperinterface(ToolArgs)
		for ((name, parameters) in meta.functions) {
			val sub =
				TypeSpec.classBuilder(name.toPascalCase()).addModifiers(KModifier.DATA).addAnnotation(Serializable)
					.addAnnotation(AnnotationSpec.builder(SerialName).addMember("%S", name).build())
					.superclass(argsType)
			val constructor = FunSpec.constructorBuilder()
			addSerializableProps(sub, constructor, parameters)
			sub.primaryConstructor(constructor.build())
			sealed.addType(sub.build())
		}
		val fileSpec =
			FileSpec.builder(argsPackage, "${namePascal}Args").addAnnotation(fileSuppress).addType(sealed.build())
		for (declared in meta.declared) fileSpec.addType(declared.toTopLevelType())
		fileSpec.build().writeTo(outputDir)
	}
	
	private fun generateDescriptions(outputDir: Path) {
		val root = TypeSpec.classBuilder("${namePascal}MetaDescriptions").addModifiers(KModifier.DATA)
		val strType = STRING
		val constructor = FunSpec.constructorBuilder()
		constructor.addParameter("toolDescription", strType)
		root.addProperty(PropertySpec.builder("toolDescription", strType).initializer("toolDescription").build())
		
		val funcsType = ClassName(toolPackage, "${namePascal}MetaDescriptions", "Functions")
		val funcsSpec = TypeSpec.classBuilder("Functions").addModifiers(KModifier.DATA)
		val funcsCtor = FunSpec.constructorBuilder()
		for ((name, parameters) in meta.functions) {
			val funcType = ClassName(toolPackage, "${namePascal}MetaDescriptions", "Functions", name.toPascalCase())
			funcsSpec.addType(funcDescClass(funcType, parameters))
			val pairType = Pair::class.asTypeName().parameterizedBy(funcType, strType)
			funcsCtor.addParameter(name.toCamelCase(), pairType)
			funcsSpec.addProperty(
				PropertySpec.builder(name.toCamelCase(), pairType).initializer(name.toCamelCase()).build()
			)
		}
		funcsSpec.primaryConstructor(funcsCtor.build())
		root.addType(funcsSpec.build())
		constructor.addParameter("functions", funcsType)
		root.addProperty(PropertySpec.builder("functions", funcsType).initializer("functions").build())
		
		val typesType = ClassName(toolPackage, "${namePascal}MetaDescriptions", "Types")
		val typesSpec = TypeSpec.classBuilder("Types").addModifiers(KModifier.DATA)
		val typesCtor = FunSpec.constructorBuilder()
		var hasTypes = false
		for (declared in meta.declared) {
			if (declared is ToolMeta.Type.Enum) continue
			val typeSpec = declared.toDescTopLevelType(toolPackage, namePascal)
			typesSpec.addType(typeSpec)
			val dName = declared.nameOf()
			val typeName = ClassName(toolPackage, "${namePascal}MetaDescriptions", "Types", dName.toPascalCase())
			typesCtor.addParameter(dName.toCamelCase(), typeName)
			typesSpec.addProperty(
				PropertySpec.builder(dName.toCamelCase(), typeName).initializer(dName.toCamelCase()).build()
			)
			hasTypes = true
		}
		if (hasTypes) {
			typesSpec.primaryConstructor(typesCtor.build())
			root.addType(typesSpec.build())
			constructor.addParameter("types", typesType)
			root.addProperty(PropertySpec.builder("types", typesType).initializer("types").build())
		}
		
		root.primaryConstructor(constructor.build())
		FileSpec.builder(toolPackage, "${namePascal}MetaDescriptions").addAnnotation(fileSuppress).addType(root.build())
			.build().writeTo(outputDir)
	}
	
	private fun funcDescClass(
		typeName: ClassName, parameters: List<ToolMeta.Prop>
	): TypeSpec = variantDescClass(typeName, parameters)
	
	private fun addSerializableProps(target: TypeSpec.Builder, ctor: FunSpec.Builder, properties: List<ToolMeta.Prop>) {
		for ((snakeName, type, required) in properties) {
			val kt = type.toKotlinType()
			val propName = snakeName.toCamelCase()
			if (required) {
				ctor.addParameter(propName, kt)
				target.addProperty(
					PropertySpec.builder(propName, kt).initializer(propName)
						.addAnnotation(
							AnnotationSpec.builder(SerialName).addMember("%S", snakeName)
								.build()
						).build()
				)
			} else {
				val pt = kt.copy(nullable = true)
				ctor.addParameter(ParameterSpec.builder(propName, pt).defaultValue("null").build())
				target.addProperty(
					PropertySpec.builder(propName, pt).initializer(propName)
						.addAnnotation(
							AnnotationSpec.builder(SerialName).addMember("%S", snakeName)
								.build()
						).build()
				)
			}
		}
	}
	
	private fun generateMetaBuilder(outputDir: Path) {
		val code = buildCodeBlock {
			block("return Pair") {
				block("%T", runtimeMeta) {
					add("name = %S,\n", meta.name)
					add("description = descriptions.toolDescription,\n")
					block("functions = listOf") {
						for ((name, parameters) in meta.functions) {
							add(functionBlock(name, parameters))
							add(",\n")
						}
					}
				}
				add(", %T.serializer()", argsType)
			}
		}
		
		val functionSpec =
			FunSpec.builder("${meta.name.toCamelCase()}Meta").addParameter("descriptions", metaType).returns(
				Pair::class.asTypeName().parameterizedBy(
					runtimeMeta, ClassName("kotlinx.serialization", "KSerializer").parameterizedBy(argsType)
				)
			).addCode(code).build()
		
		FileSpec.builder(toolPackage, "${namePascal}MetaBuilder").addAnnotation(fileSuppress).addFunction(functionSpec)
			.build().writeTo(outputDir)
	}
	
	private fun functionBlock(name: String, parameters: List<ToolMeta.Prop>): CodeBlock = buildCodeBlock {
		val desc = "descriptions.functions.${name.toCamelCase()}"
		block("%T", runtimeFunction) {
			add("name = %S,\n", name)
			add("description = $desc.second,\n")
			block("parameters = listOf") {
				for ((snakeName, type, required) in parameters) {
					add(propBlock(snakeName, type, required, "$desc.first.${snakeName.toCamelCase()}"))
					add(",\n")
				}
			}
		}
	}
	
	private fun propBlock(
		snakeName: String, type: ToolMeta.Type, required: Boolean, descriptionCall: String
	): CodeBlock = buildCodeBlock {
		block("%T", runtimeProp) {
			add("name = %S,\n", snakeName)
			add("type = %L,\n", type.toMetaTypeBlock())
			add("required = %L,\n", required)
			add("description = $descriptionCall,\n")
		}
	}
	
	private fun ToolMeta.Type.toKotlinType(): TypeName = when (this) {
		is ToolMeta.Type.TString -> STRING
		is ToolMeta.Type.TInt -> INT
		is ToolMeta.Type.TLong -> LONG
		is ToolMeta.Type.TDouble -> DOUBLE
		is ToolMeta.Type.TBoolean -> BOOLEAN
		is ToolMeta.Type.TList -> LIST.parameterizedBy(element.toKotlinType())
		is ToolMeta.Type.TMap -> MAP.parameterizedBy(
			ToolMeta.Type.TString.toKotlinType(), element.toKotlinType()
		)
		
		is ToolMeta.Type.OneOf -> ClassName(argsPackage, name.toPascalCase())
		is ToolMeta.Type.Obj -> ClassName(argsPackage, name.toPascalCase())
		is ToolMeta.Type.Enum -> ClassName(argsPackage, name.toPascalCase())
		is ToolMeta.Type.Builtin, is ToolMeta.Type.Declared -> unreachable()
	}
	
	private fun ToolMeta.Type.toMetaTypeBlock(): CodeBlock = when (this) {
		is ToolMeta.Type.TString -> CodeBlock.of("%T.TString", runtimeType)
		is ToolMeta.Type.TInt -> CodeBlock.of("%T.TInt", runtimeType)
		is ToolMeta.Type.TLong -> CodeBlock.of("%T.TLong", runtimeType)
		is ToolMeta.Type.TDouble -> CodeBlock.of("%T.TDouble", runtimeType)
		is ToolMeta.Type.TBoolean -> CodeBlock.of("%T.TBoolean", runtimeType)
		is ToolMeta.Type.TList -> CodeBlock.of("%T.TList(%L)", runtimeType, element.toMetaTypeBlock())
		is ToolMeta.Type.TMap -> CodeBlock.of("%T.TMap(%L)", runtimeType, element.toMetaTypeBlock())
		
		is ToolMeta.Type.OneOf -> oneOfBlock(this)
		is ToolMeta.Type.Obj -> objBlock(this)
		is ToolMeta.Type.Enum -> CodeBlock.of(
			"%T.Enum(%S, setOf(${values.joinToString(", ") { "\"$it\"" }}))", runtimeType, name
		)
		
		is ToolMeta.Type.Builtin, is ToolMeta.Type.Declared -> unreachable()
	}
	
	private fun oneOfBlock(oneOf: ToolMeta.Type.OneOf): CodeBlock = buildCodeBlock {
		block("%T.OneOf", runtimeType) {
			add("name = %S,\n", oneOf.name)
			block("variants = listOf") {
				for ((variantName, properties) in oneOf.variants) {
					add(variantBlock(oneOf.name, variantName, properties))
					add(",\n")
				}
			}
		}
	}
	
	private fun variantBlock(oneOfName: String, variantName: String, properties: List<ToolMeta.Prop>): CodeBlock =
		buildCodeBlock {
			val vt = runtimeMeta.nestedClass("Type").nestedClass("OneOf").nestedClass("Variant")
			val desc = "descriptions.types.${oneOfName.toCamelCase()}.${variantName.toCamelCase()}"
			block("%T", vt) {
				add("name = %S,\n", variantName)
				val empty = properties.isEmpty()
				add("description = ${if (empty) desc else "$desc.second"},\n")
				block("properties = listOf") {
					if (!empty) {
						for ((snakeName, type, required) in properties) {
							add(propBlock(snakeName, type, required, "$desc.first.${snakeName.toCamelCase()}"))
							add(",\n")
						}
					}
				}
			}
		}
	
	private fun objBlock(obj: ToolMeta.Type.Obj): CodeBlock = buildCodeBlock {
		val desc = "descriptions.types.${obj.name.toCamelCase()}"
		block("%T.Obj", runtimeType) {
			add("name = %S,\n", obj.name)
			block("properties = listOf") {
				for ((snakeName, type, required) in obj.properties) {
					add(propBlock(snakeName, type, required, "$desc.${snakeName.toCamelCase()}"))
					add(",\n")
				}
			}
		}
	}
	
	private fun ToolMeta.Type.Declared.toTopLevelType(): TypeSpec = when (this) {
		is ToolMeta.Type.OneOf -> TypeSpec.classBuilder(name.toPascalCase()).addModifiers(KModifier.SEALED)
			.addAnnotation(Serializable).apply {
				for ((variantName, properties) in variants) {
					if (properties.isEmpty()) {
						val vc = TypeSpec.objectBuilder(variantName.toPascalCase()).addModifiers(KModifier.DATA)
							.addAnnotation(AnnotationSpec.builder(SerialName).addMember("%S", variantName).build())
							.superclass(ClassName(argsPackage, name.toPascalCase()))
						addType(vc.build())
					} else {
						val vc = TypeSpec.classBuilder(variantName.toPascalCase()).addModifiers(KModifier.DATA)
							.addAnnotation(AnnotationSpec.builder(SerialName).addMember("%S", variantName).build())
							.superclass(ClassName(argsPackage, name.toPascalCase()))
						val constructor = FunSpec.constructorBuilder()
						addSerializableProps(vc, constructor, properties)
						vc.primaryConstructor(constructor.build())
						addType(vc.build())
					}
				}
			}.build()
		
		is ToolMeta.Type.Obj -> TypeSpec.classBuilder(name.toPascalCase()).addModifiers(KModifier.DATA)
			.addAnnotation(Serializable).apply {
				val constructor = FunSpec.constructorBuilder()
				addSerializableProps(this, constructor, properties)
				primaryConstructor(constructor.build())
			}.build()
		
		is ToolMeta.Type.Enum -> {
			val seen = mutableSetOf<String>()
			TypeSpec.enumBuilder(name.toPascalCase()).addAnnotation(Serializable).apply {
				for (v in values) {
					val upper = v.uppercase()
					require(upper !in seen) { "Enum value '$v' produces duplicate constant '$upper'" }
					seen.add(upper)
					addEnumConstant(
						upper,
						TypeSpec.anonymousClassBuilder()
							.addAnnotation(AnnotationSpec.builder(SerialName).addMember("%S", v).build()).build()
					)
				}
			}.build()
		}
		
		else -> error("unsupported declared type")
	}
	
	private fun ToolMeta.Type.Declared.toDescTopLevelType(descPackage: String, rootName: String): TypeSpec =
		when (this) {
			is ToolMeta.Type.OneOf -> {
				val typeName = ClassName(descPackage, "${rootName}MetaDescriptions", "Types", name.toPascalCase())
				TypeSpec.classBuilder(name.toPascalCase()).addModifiers(KModifier.DATA).apply {
					val ctor = FunSpec.constructorBuilder()
					for ((variantName, properties) in variants) {
						if (properties.isEmpty()) {
							val strType = STRING
							ctor.addParameter(variantName.toCamelCase(), strType)
							addProperty(
								PropertySpec.builder(variantName.toCamelCase(), strType)
									.initializer(variantName.toCamelCase()).build()
							)
						} else {
							val variantPascal = variantName.toPascalCase()
							val variantType = typeName.nestedClass(variantPascal)
							val pairType =
								Pair::class.asTypeName().parameterizedBy(variantType, STRING)
							ctor.addParameter(variantName.toCamelCase(), pairType)
							addProperty(
								PropertySpec.builder(variantName.toCamelCase(), pairType)
									.initializer(variantName.toCamelCase()).build()
							)
							addType(variantDescClass(variantType, properties))
						}
					}
					primaryConstructor(ctor.build())
				}.build()
			}
			
			is ToolMeta.Type.Obj -> {
				val strType = STRING
				TypeSpec.classBuilder(name.toPascalCase()).addModifiers(KModifier.DATA).apply {
					val ctor = FunSpec.constructorBuilder()
					for ((snakeName) in properties) {
						ctor.addParameter(snakeName.toCamelCase(), strType)
						addProperty(
							PropertySpec.builder(snakeName.toCamelCase(), strType).initializer(snakeName.toCamelCase())
								.build()
						)
					}
					primaryConstructor(ctor.build())
				}.build()
			}
			
			else -> unreachable()
		}
	
	private fun variantDescClass(typeName: ClassName, properties: List<ToolMeta.Prop>): TypeSpec =
		TypeSpec.classBuilder(typeName.simpleName).addModifiers(KModifier.DATA).apply {
			val ctor = FunSpec.constructorBuilder()
			val strType = STRING
			for ((snakeName) in properties) {
				ctor.addParameter(snakeName.toCamelCase(), strType)
				addProperty(
					PropertySpec.builder(snakeName.toCamelCase(), strType).initializer(snakeName.toCamelCase()).build()
				)
			}
			primaryConstructor(ctor.build())
		}.build()
	
	private fun ToolMeta.Type.Declared.nameOf(): String = when (this) {
		is ToolMeta.Type.OneOf -> name
		is ToolMeta.Type.Obj -> name
		is ToolMeta.Type.Enum -> name
		is ToolMeta.Type.TList, is ToolMeta.Type.TMap -> unreachable()
	}
	
	private inline fun CodeBlock.Builder.block(name: String, vararg args: Any?, body: () -> Unit) {
		add("$name(\n", *args)
		withIndent(body)
		add(")")
	}
	
	private inline fun CodeBlock.Builder.withIndent(body: () -> Unit) {
		indent()
		body()
		unindent()
	}
	
	private fun String.toCamelCase() = toCamelCase(this)
	private fun String.toPascalCase() = toPascalCase(this)
	
	private fun unreachable(): Nothing = error("unreachable")
	
	companion object {
		private val Serializable = ClassName("kotlinx.serialization", "Serializable")
		private val SerialName = ClassName("kotlinx.serialization", "SerialName")
		private val ToolArgs = ClassName("io.github.autotweaker.api.tool", "ToolArgs")
		
		internal fun toCamelCase(snake: String): String {
			val pascal = snake.split('_').joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
			return pascal.replaceFirstChar { it.lowercaseChar() }
		}
		
		internal fun toPascalCase(snake: String): String =
			snake.split('_').joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }
	}
}
