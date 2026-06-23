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

package io.github.autotweaker.api.types

data class SemVer(
	val major: Int,
	val minor: Int,
	val patch: Int,
	val preRelease: List<String> = emptyList(),
	val buildMetadata: List<String> = emptyList()
) : Comparable<SemVer> {
	init {
		require(major >= 0) { "Major version must be non-negative, got: $major" }
		require(minor >= 0) { "Minor version must be non-negative, got: $minor" }
		require(patch >= 0) { "Patch version must be non-negative, got: $patch" }
		validatePreRelease()
		validateBuildMetadata()
	}
	
	private fun validatePreRelease() {
		for (id in preRelease) {
			require(id.isNotEmpty()) { "Pre-release identifier must not be empty" }
			require(id.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' || it == '-' }) {
				"Pre-release identifier must consist of [0-9A-Za-z-], got: \"$id\""
			}
			if (id.all { it in '0'..'9' }) {
				require(id == "0" || !id.startsWith('0')) {
					"Numeric pre-release identifier must not have leading zeros, got: \"$id\""
				}
			}
		}
	}
	
	private fun validateBuildMetadata() {
		for (id in buildMetadata) {
			require(id.isNotEmpty()) { "Build metadata identifier must not be empty" }
			require(id.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' || it == '-' }) {
				"Build metadata identifier must consist of [0-9A-Za-z-], got: \"$id\""
			}
		}
	}
	
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is SemVer) return false
		return major == other.major && minor == other.minor && patch == other.patch && preRelease == other.preRelease
	}
	
	override fun hashCode(): Int {
		var result = major
		result = 31 * result + minor
		result = 31 * result + patch
		result = 31 * result + preRelease.hashCode()
		return result
	}
	
	override fun compareTo(other: SemVer): Int {
		if (major != other.major) return major.compareTo(other.major)
		if (minor != other.minor) return minor.compareTo(other.minor)
		if (patch != other.patch) return patch.compareTo(other.patch)
		
		if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
		if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
		
		for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
			val a = preRelease[i]
			val b = other.preRelease[i]
			
			val aNum = a.toIntOrNull()
			val bNum = b.toIntOrNull()
			
			val cmp = when {
				aNum != null && bNum != null -> aNum.compareTo(bNum)
				aNum != null -> -1
				bNum != null -> 1
				else -> a.compareTo(b)
			}
			if (cmp != 0) return cmp
		}
		
		return preRelease.size.compareTo(other.preRelease.size)
	}
	
	override fun toString(): String = buildString {
		append(major); append('.'); append(minor); append('.'); append(patch)
		if (preRelease.isNotEmpty()) {
			append('-')
			append(preRelease.joinToString("."))
		}
		if (buildMetadata.isNotEmpty()) {
			append('+')
			append(buildMetadata.joinToString("."))
		}
	}
	
	companion object {
		private val regex = Regex(
			"^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
					"(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
					"(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
					"(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
		)
		
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
