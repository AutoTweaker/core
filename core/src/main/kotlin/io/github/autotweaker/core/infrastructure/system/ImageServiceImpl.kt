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

package io.github.autotweaker.core.infrastructure.system

import com.google.auto.service.AutoService
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.base.LongSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.get
import io.github.autotweaker.api.log
import io.github.autotweaker.core.domain.port.ImageContent
import io.github.autotweaker.core.domain.port.ImageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageInputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

object ImageServiceImpl : ImageService, Loggable {
	private const val JPEG = "jpeg"
	private const val PNG = "png"
	private const val MIN_EDGE = 64
	private const val DEFAULT_QUALITY = 0.85f
	private const val MIN_QUALITY = 0.3f
	
	override suspend fun detect(path: Path): String = withContext(Dispatchers.IO) {
		open(path).use { it.mimeType }
	}
	
	override suspend fun read(path: Path): ImageContent = withContext(Dispatchers.IO) {
		val limit = MaxSizeKB().get() * 1024
		open(path).use { image ->
			if (image.fits(Files.size(path), limit)) {
				return@withContext ImageContent(image.mimeType, Files.readAllBytes(path))
			}
			
			val alpha = image.hasAlpha
			val format = if (alpha) PNG else JPEG
			val decoded = normalize(image.decode(decodeBudget(limit)), alpha)
			val bytes = fit(decoded, alpha, limit, format)
			
			log.debug(
				"Re-encoded image  path={}  source={}  target={}  bytes={}",
				path, image.mimeType, mimeOf(format), bytes.size
			)
			ImageContent(mimeOf(format), bytes)
		}
	}
	
	private fun mimeOf(formatName: String) = when (val name = formatName.lowercase()) {
		"jpg" -> "image/jpeg"
		"tif" -> "image/tiff"
		else -> "image/$name"
	}
	
	private fun decodeBudget(limit: Long) = (limit * 10).coerceIn(4_000_000L, 32_000_000L)
	
	private fun encode(image: BufferedImage, format: String, quality: Float): ByteArray {
		val writer = ImageIO.getImageWritersByFormatName(format).next()
		val param = writer.defaultWriteParam
		if (format == JPEG) {
			param.compressionMode = ImageWriteParam.MODE_EXPLICIT
			param.compressionQuality = quality
		}
		
		val bytes = ByteArrayOutputStream()
		ImageIO.createImageOutputStream(bytes).use { output ->
			writer.output = output
			writer.write(null, IIOImage(image, null, null), param)
		}
		writer.dispose()
		return bytes.toByteArray()
	}
	
	private fun normalize(source: BufferedImage, alpha: Boolean): BufferedImage {
		val type = if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
		if (source.type == type) return source
		
		val target = BufferedImage(source.width, source.height, type)
		val graphics = target.createGraphics()
		graphics.drawImage(source, 0, 0, null)
		graphics.dispose()
		return target
	}
	
	private fun shrink(source: BufferedImage, alpha: Boolean, targetEdge: Int): BufferedImage {
		val ratio = targetEdge.toDouble() / max(source.width, source.height)
		val width = max((source.width * ratio).toInt(), 1)
		val height = max((source.height * ratio).toInt(), 1)
		val type = if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
		
		val target = BufferedImage(width, height, type)
		val graphics = target.createGraphics()
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
		graphics.drawImage(source, 0, 0, width, height, null)
		graphics.dispose()
		return target
	}
	
	private fun fitQuality(image: BufferedImage, limit: Long): ByteArray? {
		var low = MIN_QUALITY
		var high = DEFAULT_QUALITY
		var best = encode(image, JPEG, MIN_QUALITY).takeIf { it.size <= limit } ?: return null
		
		repeat(7) {
			val quality = (low + high) / 2
			val data = encode(image, JPEG, quality)
			if (data.size <= limit) {
				best = data
				low = quality
			} else {
				high = quality
			}
		}
		return best
	}
	
	private suspend fun fit(source: BufferedImage, alpha: Boolean, limit: Long, format: String): ByteArray {
		var image = source
		while (true) {
			currentCoroutineContext().ensureActive()
			
			val edge = max(image.width, image.height)
			if (edge <= MIN_EDGE) return encode(image, format, MIN_QUALITY)
			
			val probe = encode(image, format, DEFAULT_QUALITY)
			if (probe.size <= limit) return probe
			if (format == JPEG) fitQuality(image, limit)?.let { return it }
			
			val ratio = sqrt(limit.toDouble() / probe.size) * 0.95
			image = shrink(image, alpha, max((edge * ratio).toInt(), MIN_EDGE))
		}
	}
	
	private fun open(path: Path): OpenedImage {
		val stream = ImageIO.createImageInputStream(path.toFile())
			?: throw IllegalStateException("Cannot read image: $path")
		val readers = ImageIO.getImageReaders(stream)
		if (!readers.hasNext()) {
			stream.close()
			throw IllegalStateException("Cannot read image: $path")
		}
		
		val reader = readers.next()
		reader.input = stream
		return OpenedImage(reader, stream, mimeOf(reader.formatName))
	}
	
	private class OpenedImage(
		private val reader: ImageReader,
		private val stream: ImageInputStream,
		val mimeType: String,
	) : AutoCloseable {
		val width = reader.getWidth(0)
		val height = reader.getHeight(0)
		val hasAlpha = reader.getImageTypes(0).next().colorModel.hasAlpha()
		
		fun fits(size: Long, limit: Long) =
			size <= limit && mimeType in setOf("image/jpeg", "image/png", "image/gif", "image/webp")
		
		fun decode(budget: Long): BufferedImage {
			val param = reader.defaultReadParam
			val total = width.toLong() * height
			if (total > budget) {
				val factor = ceil(sqrt(total.toDouble() / budget)).toInt()
				param.setSourceSubsampling(factor, factor, 0, 0)
			}
			return reader.read(0, param)
		}
		
		override fun close() {
			reader.dispose()
			stream.close()
		}
	}
	
	@AutoService(SettingDef::class)
	class MaxSizeKB : LongSetting(
		1024, zh(
			"图片读取后的体积上限，单位KB，超出上限时会降低分辨率与质量重新编码"
		)
	)
}
