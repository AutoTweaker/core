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

package io.github.autotweaker.core.llm.provider.mimo

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiMoDataClassCoverageTest {
	
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
	
	@Test
	fun `MiMoUsage all fields`() {
		val usage = MiMoUsage(
			completionTokens = 50, promptTokens = 50, totalTokens = 100,
			completionTokensDetails = MiMoUsage.CompletionTokensDetails(reasoningTokens = 20),
			promptTokensDetails = MiMoUsage.PromptTokensDetails(
				cachedTokens = 10, audioTokens = null, imageTokens = null, videoTokens = null
			)
		)
		assertEquals(100, usage.totalTokens)
		assertEquals(20, usage.completionTokensDetails?.reasoningTokens)
		assertEquals(10, usage.promptTokensDetails?.cachedTokens)
	}
	
	@Test
	fun `MiMoUsage minimal fields`() {
		val usage = MiMoUsage(completionTokens = 10, promptTokens = 20, totalTokens = 30)
		assertEquals(30, usage.totalTokens)
		assertNull(usage.completionTokensDetails)
	}
	
	@Test
	fun `MiMoUsage CompletionTokensDetails`() {
		val details = MiMoUsage.CompletionTokensDetails(reasoningTokens = 42)
		assertEquals(42, details.reasoningTokens)
	}
	
	@Test
	fun `MiMoUsage PromptTokensDetails all fields`() {
		val details = MiMoUsage.PromptTokensDetails(
			cachedTokens = 5, audioTokens = 10, imageTokens = 15, videoTokens = 20
		)
		assertEquals(5, details.cachedTokens)
		assertEquals(10, details.audioTokens)
		assertEquals(15, details.imageTokens)
		assertEquals(20, details.videoTokens)
	}
	
	@Test
	fun `MiMoFinishReason enum values`() {
		assertEquals("stop", MiMoFinishReason.STOP.value)
		assertEquals("length", MiMoFinishReason.LENGTH.value)
		assertEquals("tool_calls", MiMoFinishReason.TOOL_CALLS.value)
		assertEquals("content_filter", MiMoFinishReason.CONTENT_FILTER.value)
		assertEquals("repetition_truncation", MiMoFinishReason.REPETITION_TRUNCATION.value)
	}
	
	@Test
	fun `MiMoToolCall create`() {
		val tc = MiMoToolCall("tc1", function = MiMoToolCall.Function("read", "{}"))
		assertEquals("tc1", tc.id)
		assertEquals("read", tc.function.name)
		assertEquals("{}", tc.function.arguments)
	}
	
	@Test
	fun `MiMoRequest deserialize`() {
		val req = json.decodeFromString<MiMoRequest>(
			"""{
            "model":"mimo-v2-pro","messages":[{"role":"user","content":[{"type":"text","text":"hi"}]}],
            "stream":true,"temperature":0.7,"max_completion_tokens":500,
            "top_p":0.9,"frequency_penalty":0.1,"presence_penalty":0.2,
            "tool_choice":"auto"
        }"""
		)
		assertEquals("mimo-v2-pro", req.model)
		assertEquals(true, req.stream)
		assertEquals(0.7, req.temperature)
		assertEquals(500, req.maxCompletionTokens)
	}
	
	@Test
	fun `MiMoResponse deserialize`() {
		val resp = json.decodeFromString<MiMoResponse>(
			"""{
            "id":"r1","created":1715678901,"model":"mimo",
            "choices":[{"index":0,"message":{"content":"hello","reasoning_content":"think"},"finish_reason":"stop"}],
            "usage":{"completion_tokens":10,"prompt_tokens":20,"total_tokens":30}
        }"""
		)
		assertEquals("r1", resp.id)
		assertEquals("hello", resp.choices[0].message.content)
		assertEquals("think", resp.choices[0].message.reasoningContent)
		assertEquals(30, resp.usage.totalTokens)
	}
	
	@Test
	fun `MiMoStreamChunk deserialize`() {
		val chunk = json.decodeFromString<MiMoStreamChunk>(
			"""{
            "id":"c1","created":1715678901,"model":"mimo",
            "choices":[{"index":0,"delta":{"content":"partial","reasoning_content":"thinking"},"finish_reason":null}],
            "usage":{"completion_tokens":5,"prompt_tokens":10,"total_tokens":15}
        }"""
		)
		assertEquals("c1", chunk.id)
		assertEquals("partial", chunk.choices[0].delta.content)
		assertEquals("thinking", chunk.choices[0].delta.reasoningContent)
		assertNull(chunk.choices[0].finishReason)
	}
	
	@Test
	fun `MiMoStreamChunk with finish reason`() {
		val chunk = json.decodeFromString<MiMoStreamChunk>(
			"""{
            "id":"c1","created":1715678901,"model":"mimo",
            "choices":[{"index":0,"delta":{"content":"done"},"finish_reason":"stop"}],
            "usage":{"completion_tokens":5,"prompt_tokens":10,"total_tokens":15,
                "completion_tokens_details":{"reasoning_tokens":3},
                "prompt_tokens_details":{"cached_tokens":2}}
        }"""
		)
		assertEquals(MiMoFinishReason.STOP, chunk.choices[0].finishReason)
		assertEquals(3, chunk.usage!!.completionTokensDetails?.reasoningTokens)
		assertEquals(2, chunk.usage.promptTokensDetails?.cachedTokens)
	}
	
	@Test
	fun `MiMoUsage deserialize with details`() {
		val usage = json.decodeFromString<MiMoUsage>(
			"""{
            "completion_tokens":50,"prompt_tokens":50,"total_tokens":100,
            "completion_tokens_details":{"reasoning_tokens":10},
            "prompt_tokens_details":{"cached_tokens":20,"audio_tokens":1,"image_tokens":2,"video_tokens":3}
        }"""
		)
		assertEquals(10, usage.completionTokensDetails?.reasoningTokens)
		assertEquals(20, usage.promptTokensDetails?.cachedTokens)
		assertEquals(1, usage.promptTokensDetails?.audioTokens)
		assertEquals(2, usage.promptTokensDetails?.imageTokens)
		assertEquals(3, usage.promptTokensDetails?.videoTokens)
	}
	
	@Test
	fun `MiMoFinishReason deserialize from JSON`() {
		assertEquals(MiMoFinishReason.STOP, json.decodeFromString<MiMoFinishReason>("\"stop\""))
		assertEquals(MiMoFinishReason.LENGTH, json.decodeFromString<MiMoFinishReason>("\"length\""))
		assertEquals(MiMoFinishReason.TOOL_CALLS, json.decodeFromString<MiMoFinishReason>("\"tool_calls\""))
		assertEquals(MiMoFinishReason.CONTENT_FILTER, json.decodeFromString<MiMoFinishReason>("\"content_filter\""))
		assertEquals(
			MiMoFinishReason.REPETITION_TRUNCATION,
			json.decodeFromString<MiMoFinishReason>("\"repetition_truncation\"")
		)
	}
	
	@Test
	fun `MiMoMessage Content types`() {
		val text = MiMoMessage.Content.TextPart("hello")
		assertEquals("hello", text.text)
		
		val image = MiMoMessage.Content.ImagePart(MiMoMessage.Content.ImagePart.Url("https://example.com/img.png"))
		assertEquals("https://example.com/img.png", image.imageUrl.url)
		
		val audio = MiMoMessage.Content.AudioPart(MiMoMessage.Content.AudioPart.Data("base64data"))
		assertEquals("base64data", audio.inputAudio.data)
		
		val video = MiMoMessage.Content.VideoPart(
			MiMoMessage.Content.VideoPart.Url("https://example.com/video.mp4"),
			fps = 30
		)
		assertEquals("https://example.com/video.mp4", video.videoUrl.url)
		assertEquals(30, video.fps)
	}
}
