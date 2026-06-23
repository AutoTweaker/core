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

package io.github.autotweaker.core.infrastructure.data

import io.github.autotweaker.api.*
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.core.domain.port.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

object SecretManager : SecretStore, Loggable, Traceable, Settable {
	private val rootDir = Path.of(
		System.getProperty("user.home"), ".config", "autotweaker", "secret"
	)
	private val secretsDir = rootDir.resolve("secrets")
	private val gpgHome = rootDir.resolve(".gnupg")
	private val markerFile = rootDir.resolve(".verify")
	
	private const val KEY_UID = "AutoTweaker(core.infrastructure.data)@autogen.local"
	
	private val mutex = Mutex()
	
	@Volatile
	private var password: CharArray? = null
	
	private val _isUnlocked = MutableStateFlow(false)
	val isUnlocked = _isUnlocked.asStateFlow()
	val isPasswordEmpty: Boolean get() = password?.isEmpty() == true
	
	fun killGpgAgent() = trace.catching {
		val killPb = ProcessBuilder("gpgconf", "--kill", "gpg-agent")
		killPb.environment()["GNUPGHOME"] = gpgHome.toString()
		killPb.start().waitFor()
	}.onFailure { log.debug("Failed gpg-agent kill  reason={}", it.message) }.discard()
	
	suspend fun init() = try {
		unlock("")
	} catch (e: Exception) {
		trace.exception(e)
		if (hasSecretKey()) {
			log.info("Skipped SecretManager auto-unlock  reason=password_required")
		} else throw e
	}
	
	suspend fun unlock(passphrase: String) = mutex.withLock {
		//确保目录存在
		Files.createDirectories(secretsDir)
		Files.createDirectories(gpgHome)
		//确保权限正确
		trace.catching {
			Files.setPosixFilePermissions(
				gpgHome, PosixFilePermissions.fromString("rwx------")
			)
		}.onFailure { log.debug("Failed gpgHome permissions set  path={}  reason={}", gpgHome, it.message) }
		//创建私钥目录
		val privateKeysDir = gpgHome.resolve("private-keys-v1.d")
		Files.createDirectories(privateKeysDir)
		trace.catching {
			Files.setPosixFilePermissions(
				privateKeysDir, PosixFilePermissions.fromString("rwx------")
			)
		}.onFailure {
			log.debug(
				"Failed privateKeysDir permissions set  path={}  reason={}",
				privateKeysDir,
				it.message
			)
		}
		
		//创建gpg agent配置
		val agentConf = gpgHome.resolve("gpg-agent.conf")
		if (!Files.exists(agentConf)) {
			trace.catching {
				Files.writeString(agentConf, "allow-loopback-pinentry\n")
				Files.setPosixFilePermissions(agentConf, PosixFilePermissions.fromString("rw-------"))
			}.onFailure {
				log.debug("Failed gpg-agent.conf creation  path={}  reason={}", agentConf, it.message)
			}
		}
		
		if (!hasSecretKey()) {
			//无密钥
			killGpgAgent()
			setPassword(passphrase)
			generateKey()
			createMarker()
			log.info("Generated secret key  keyUid={}", KEY_UID)
		} else {
			//有密钥
			verifyPassword(passphrase)
			setPassword(passphrase)
		}.andLog(log) { info("Unlocked SecretManager  hasPassword={}", !isPasswordEmpty) }
	}
	
	//检查gpg密钥存在
	private suspend fun hasSecretKey(): Boolean = trace.catching {
		gpg("--list-secret-keys", "--with-colons", KEY_UID).lines().any {
			it.startsWith("sec:")
		}
	}.onFailure { log.warn("Failed secret key existence check  keyUid={}", KEY_UID) }
		.getOrDefault(false)
	
	//生成gpg密钥
	private suspend fun generateKey() =
		gpg(
			"--batch",
			"--pinentry-mode",
			"loopback",
			"--quick-generate-key",
			KEY_UID,
			"rsa4096",
			"encrypt",
			"never",
			passphrase = String(requireUnlocked())
		).discard()
	
	
	//加密一个ok，写入markerFile
	private suspend fun createMarker() = encryptTo("ok", markerFile)
	
	//解密markerFile，如果成功就是密钥正确
	private suspend fun verifyPassword(password: String) {
		val result =
			gpg("--batch", "--yes", "--pinentry-mode", "loopback", "-d", markerFile.toString(), passphrase = password)
		check(result == "ok") { "Invalid password" }
	}
	
	//更改密码
	suspend fun changePassword(
		oldPassword: String, newPassword: String
	) = mutex.withLock {
		val current = String(requireUnlocked())
		if (oldPassword != current) error("Invalid password")
		if (newPassword == current) return@withLock
		val cache = list().associateWith { get(it) }
		log.info("Started password change  secretCount={}", cache.size)
		deleteKey()
		Files.deleteIfExists(markerFile)
		setPassword(newPassword)
		generateKey()
		cache.forEach { (id, secret) -> encryptTo(secret, secretsDir.resolve("$id.gpg")) }
		createMarker()
		log.info("Changed password  secretCount={}", cache.size)
	}
	
	private suspend fun fingerprint(): String =
		gpg("--list-keys", "--with-colons", "--fingerprint", KEY_UID).lines().first { it.startsWith("fpr:") }.split(":")
			.getOrNull(9) ?: error("Cannot find fingerprint for $KEY_UID")
	
	//删除密钥
	private suspend fun deleteKey() =
		gpg("--batch", "--yes", "--delete-secret-and-public-key", fingerprint()).discard()
	
	//加密
	private suspend fun encryptTo(input: String, output: Path) =
		gpg(
			"--batch",
			"--yes",
			"--trust-model",
			"direct",
			"-r",
			KEY_UID,
			"-a",
			"-e",
			"-o",
			output.toString(),
			input = input
		).discard()
	
	private suspend fun gpg(
		vararg args: String, input: String? = null, passphrase: String? = null
	): String = withContext(Dispatchers.IO) {
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
		check(proc.waitFor() == 0)
		{ "GPG command failed (${cmd.joinToString(" ")}): $stderr" }
		return@withContext stdout
	}
	
	private fun setPassword(passphrase: String?) {
		password = passphrase?.toCharArray()
		_isUnlocked.value = passphrase != null
	}
	
	//确保unlocked，否则异常抛到上游
	private fun requireUnlocked() = checkNotNull(password)
	{ "SecretManager is locked. Call unlock() first." }
	
	// region 实现接口
	override suspend fun add(secret: String, id: UUID): UUID = mutex.withLock {
		requireUnlocked()
		val file = secretsDir.resolve("$id.gpg")
		encryptTo(secret, file)
		log.debug("Added secret  id={}", id)
		return id
	}
	
	override suspend fun get(id: UUID): String = mutex.withLock {
		val file = secretsDir.resolve("$id.gpg")
		check(Files.exists(file)) { "Secret not found: $id" }
		log.debug("Retrieved secret  id={}", id)
		return gpg(
			"--batch",
			"--yes",
			"--pinentry-mode",
			"loopback",
			"-d",
			file.toString(),
			passphrase = String(requireUnlocked())
		)
	}
	
	override suspend fun list(): List<UUID> = mutex.withLock {
		requireUnlocked()
		return Files.list(secretsDir).use { stream ->
			stream.filter { it.fileName.toString().endsWith(".gpg") }
				.map { UUID.fromString(it.fileName.toString().removeSuffix(".gpg")) }.toList()
		}
	}
	
	override suspend fun remove(id: UUID) = mutex.withLock {
		requireUnlocked()
		Files.deleteIfExists(secretsDir.resolve("$id.gpg"))
		log.debug("Removed secret  id={}", id)
	}
	// endregion
}
