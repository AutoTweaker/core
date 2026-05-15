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

package io.github.autotweaker.core.secret.impl

import io.github.autotweaker.core.secret.SecretStore
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

object SecretManager : SecretStore {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val rootDir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "secret")
	private val secretsDir = rootDir.resolve("secrets")
	private val gpgHome = rootDir.resolve(".gnupg")
	private val markerFile = rootDir.resolve(".verify")
	private const val KEY_UID = "AutoTweaker(core.secret)@autogen.local"
	
	@Volatile
	private var password: String? = null
	
	val isUnlocked: Boolean get() = password != null
	val isPasswordEmpty: Boolean get() = password == ""
	
	fun killGpgAgent() {
		runCatching {
			val killPb = ProcessBuilder("gpgconf", "--kill", "gpg-agent")
			killPb.environment()["GNUPGHOME"] = gpgHome.toString()
			killPb.start().waitFor()
		}
	}
	
	fun init() {
		try {
			unlock("")
		} catch (e: Exception) {
			if (hasSecretKey()) {
				logger.info("SecretManager auto-unlock skipped  reason=password_required")
				return
			}
			throw e
		}
	}
	
	fun unlock(password: String) {
		//确保目录存在
		Files.createDirectories(secretsDir)
		Files.createDirectories(gpgHome)
		//确保权限正确
		try {
			Files.setPosixFilePermissions(gpgHome, PosixFilePermissions.fromString("rwx------"))
		} catch (_: UnsupportedOperationException) {
		}
		//创建私钥目录
		val privateKeysDir = gpgHome.resolve("private-keys-v1.d")
		Files.createDirectories(privateKeysDir)
		try {
			Files.setPosixFilePermissions(privateKeysDir, PosixFilePermissions.fromString("rwx------"))
		} catch (_: UnsupportedOperationException) {
		}
		//创建gpg agent配置
		val agentConf = gpgHome.resolve("gpg-agent.conf")
		if (!Files.exists(agentConf)) {
			Files.writeString(agentConf, "allow-loopback-pinentry\n")
			Files.setPosixFilePermissions(agentConf, PosixFilePermissions.fromString("rw-------"))
		}
		//干掉gpg agent
		runCatching {
			val killPb = ProcessBuilder("gpgconf", "--kill", "gpg-agent")
			killPb.environment()["GNUPGHOME"] = gpgHome.toString()
			killPb.start().waitFor()
		}
		if (!hasSecretKey()) {
			this.password = password
			generateKey()
			createMarker()
			logger.info("Secret key generated")
		} else {
			verifyPassword(password)
			this.password = password
		}
		logger.info("SecretManager unlocked  keyExists={}", hasSecretKey())
	}
	
	//确保unlocked，否则异常抛到上游
	private fun requireUnlocked() = check(password != null) { "SecretManager is locked. Call unlock() first." }
	
	//检查gpg密钥存在
	private fun hasSecretKey(): Boolean = try {
		gpg("--list-secret-keys", "--with-colons", KEY_UID).lines().any {
			it.startsWith("sec:")
		}
	} catch (_: Exception) {
		false
	}
	
	//生成gpg密钥
	private fun generateKey() = gpg(
		"--batch",
		"--pinentry-mode",
		"loopback",
		"--quick-generate-key",
		KEY_UID,
		"rsa4096",
		"encrypt",
		"never",
		passphrase = password!!
	)
	
	//加密一个ok，写入markerFile
	private fun createMarker() = encryptTo("ok", markerFile)
	
	//解密markerFile，如果成功就是密钥正确
	private fun verifyPassword(password: String) {
		val result =
			gpg("--batch", "--yes", "--pinentry-mode", "loopback", "-d", markerFile.toString(), passphrase = password)
		check(result == "ok") { "Invalid password" }
	}
	
	//更改密码
	fun changePassword(oldPassword: String, newPassword: String) {
		requireUnlocked()
		if (oldPassword != password) error("Invalid password")
		if (newPassword == password) return
		val cache = list().associateWith { get(it) }
		logger.info("Password change started  secretCount={}", cache.size)
		deleteKey()
		Files.deleteIfExists(markerFile)
		this.password = newPassword
		generateKey()
		cache.forEach { (id, secret) -> encryptTo(secret, secretsDir.resolve("$id.gpg")) }
		createMarker()
		logger.info("Password changed  secretCount={}", cache.size)
	}
	
	private fun fingerprint(): String =
		gpg("--list-keys", "--with-colons", "--fingerprint", KEY_UID).lines().first { it.startsWith("fpr:") }.split(":")
			.getOrNull(9) ?: error("Cannot find fingerprint for $KEY_UID")
	
	//删除密钥
	private fun deleteKey() = gpg("--batch", "--yes", "--delete-secret-and-public-key", fingerprint())
	
	//加密
	private fun encryptTo(input: String, output: Path) = gpg(
		"--batch", "--yes", "--trust-model", "always", "-r", KEY_UID, "-a", "-e", "-o", output.toString(), input = input
	)
	
	private fun gpg(vararg args: String, input: String? = null, passphrase: String? = null): String {
		val allArgs = mutableListOf("gpg")
		if (passphrase != null) {
			allArgs += listOf("--passphrase-fd", "0")
		}
		allArgs.addAll(args)
		val cmd = allArgs.toList()
		val pb = ProcessBuilder(cmd)
		pb.environment()["GNUPGHOME"] = gpgHome.toString()
		pb.environment().remove("GPG_AGENT_INFO")
		val proc = pb.start()
		proc.outputStream.bufferedWriter().use { writer ->
			if (passphrase != null) {
				writer.write(passphrase)
				writer.newLine()
			}
			if (input != null) {
				writer.write(input)
			}
		}
		val stdout = proc.inputStream.bufferedReader().readText()
		val stderr = proc.errorStream.bufferedReader().readText()
		check(proc.waitFor() == 0) { "GPG command failed (${cmd.joinToString(" ")}): $stderr" }
		return stdout
	}
	
	// region 实现接口
	override fun add(secret: String): UUID {
		requireUnlocked()
		val id = UUID.randomUUID()
		val file = secretsDir.resolve("$id.gpg")
		encryptTo(secret, file)
		logger.debug("Secret added  id={}", id)
		return id
	}
	
	override fun get(id: UUID): String {
		requireUnlocked()
		val file = secretsDir.resolve("$id.gpg")
		require(Files.exists(file)) { "Secret not found: $id" }
		logger.debug("Secret retrieved  id={}", id)
		return gpg("--batch", "--yes", "--pinentry-mode", "loopback", "-d", file.toString(), passphrase = password!!)
	}
	
	override fun list(): List<UUID> {
		requireUnlocked()
		return Files.list(secretsDir).use { stream ->
			stream.filter { it.fileName.toString().endsWith(".gpg") }
				.map { UUID.fromString(it.fileName.toString().removeSuffix(".gpg")) }.toList()
		}
	}
	
	override fun remove(id: UUID) {
		requireUnlocked()
		Files.deleteIfExists(secretsDir.resolve("$id.gpg"))
		logger.debug("Secret removed  id={}", id)
	}
	// endregion
}