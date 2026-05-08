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

package io.github.autotweaker.core.adapter.api.data

data class SemVer(
	val major: Int,
	val minor: Int,
	val patch: Int,
	val preRelease: List<String> = emptyList(),
	val buildMetadata: List<String> = emptyList()
) : Comparable<SemVer> {
	override fun compareTo(other: SemVer): Int {
		if (major != other.major) return major - other.major
		if (minor != other.minor) return minor - other.minor
		if (patch != other.patch) return patch - other.patch
		
		
		if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
		if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
		
		for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
			val a = preRelease[i]
			val b = other.preRelease[i]
			
			val aNum = a.toIntOrNull()
			val bNum = b.toIntOrNull()
			
			val cmp = when {
				aNum != null && bNum != null -> aNum - bNum
				aNum != null -> -1
				bNum != null -> 1
				else -> a.compareTo(b)
			}
			if (cmp != 0) return cmp
		}
		
		return preRelease.size - other.preRelease.size
	}
	
	override fun toString(): String {
		val base = "$major.$minor.$patch"
		val pre = if (preRelease.isNotEmpty()) "-" + preRelease.joinToString(".") else ""
		val build = if (buildMetadata.isNotEmpty()) "+" + buildMetadata.joinToString(".") else ""
		return base + pre + build
	}
	
	companion object {
		private val regex = Regex(
			"""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?$"""
		)
		
		@Suppress("unused")
		fun parse(text: String): SemVer {
			val m = regex.matchEntire(text)
				?: error("Invalid SemVer: $text")
			
			val (maj, min, pat, pre, build) = m.destructured
			return SemVer(
				maj.toInt(),
				min.toInt(),
				pat.toInt(),
				pre.split('.').filter { it.isNotEmpty() },
				build.split('.').filter { it.isNotEmpty() }
			)
		}
	}
}
