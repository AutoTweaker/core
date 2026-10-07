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

package io.github.autotweaker.api.tool

import io.github.autotweaker.api.I18nable
import io.github.autotweaker.api.discard
import io.github.autotweaker.api.i18n.I18nDef
import io.github.autotweaker.api.types.llm.ContentPart
import io.github.autotweaker.api.types.tool.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import java.nio.file.Path

fun buildOutput(block: ResultBuilder.() -> Unit) = ResultBuilder().apply(block).build()

class ResultBuilder : I18nable {
	var success: Boolean? = null
	
	private val content = mutableListOf<ContentPart>()
	private val presentation = mutableListOf<UiBlock>()
	private var data: JsonElement? = null
	
	fun content(content: String) = this.content.add(ContentPart.Text(content)).discard()
	fun content(content: ContentPart) = this.content.add(content).discard()
	fun content(content: List<ContentPart>) = this.content.addAll(content).discard()
	
	inline fun <reified T> data(data: T) = data(serializer<T>(), data)
	
	fun <T> data(serializer: KSerializer<T>, data: T) {
		this.data = Json.encodeToJsonElement(serializer, data)
	}
	
	fun text(content: String) = presentation.text(content)
	fun text(def: I18nDef, vararg args: Any?) = presentation.text(def, *args)
	
	fun command(command: String) = presentation.command(command)
	fun diff(filePath: Path, oldContent: String?, newContent: String) =
		presentation.diff(filePath, oldContent, newContent)
	
	fun output(content: String) = presentation.output(content)
	fun error(content: String) = presentation.error(content)
	
	internal fun build(): Tool.ToolOutput {
		val success = checkNotNull(success)
		check(content.isNotEmpty())
		check(presentation.any { it is UiBlock.Text })
		return Tool.ToolOutput(
			content, presentation, data, success
		)
	}
}
