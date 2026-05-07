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

package io.github.autotweaker.core.secret

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.*

@Suppress("unused")
object SecretManager : SecretStore {
	private val rootDir = Path.of(System.getProperty("user.home"), ".config", "autotweaker", "secret")
	private val secretsDir = rootDir.resolve("secrets")
	private val gpgHome = rootDir.resolve(".gnupg")
	private val markerFile = rootDir.resolve(".verify")
	private const val KEY_UID = "AutoTweaker(core.secret)@autogen.local"
	
	@Volatile
	private var password: String? = null
	
	val isUnlocked: Boolean get() = password != null
	val isPasswordEmpty: Boolean get() = password == ""
	
	init {
		try {
			unlock("")
		} catch (_: Exception) {
		}
	}
	
	//接收密码
	fun unlock(password: String) {
		//确保目录存在
		Files.createDirectories(secretsDir)
		Files.createDirectories(gpgHome)
		//确保权限正确
		try {
			Files.setPosixFilePermissions(gpgHome, PosixFilePermissions.fromString("rwx------"))
		} catch (_: UnsupportedOperationException) {
		}
		if (!hasSecretKey()) {
			//不存在gpg密钥，生成
			this.password = password
			generateKey()
			createMarker()
		} else {
			//存在gpg密钥，验证密码
			verifyPassword(password)
			this.password = password
		}
	}
	
	//确保unlocked，否则异常抛到上游
	private fun requireUnlocked() =
		check(password != null) { "SecretManager is locked. Call unlock() first." }
	
	//检查gpg密钥存在
	private fun hasSecretKey(): Boolean =
		gpg("--list-secret-keys", "--with-colons", KEY_UID).lines().any {
			it.startsWith("sec:")
		}
	
	//生成gpg密钥
	private fun generateKey() =
		gpg(
			"--batch",
			"--passphrase", password!!,
			"--quick-generate-key", KEY_UID,
			"rsa4096", "encrypt", "never"
		)
	
	//加密一个ok，写入markerFile
	private fun createMarker() = encryptTo("ok", markerFile)
	
	//解密markerFile，如果成功就是密钥正确
	private fun verifyPassword(password: String) {
		val result = gpg("--batch", "--yes", "--passphrase", password, "-d", markerFile.toString())
		check(result == "ok") { "Invalid password" }
	}
	
	//更改密码
	fun changePassword(oldPassword: String, newPassword: String) {
		requireUnlocked()
		//改了个寂寞
		if (oldPassword == newPassword) return
		//校验密码
		verifyPassword(oldPassword)
		//解密所有密文
		val cache = list().associateWith { get(it) }
		//删除密钥
		deleteKey()
		//删除markerFile
		Files.deleteIfExists(markerFile)
		//更新密码
		this.password = newPassword
		//生成新密钥
		generateKey()
		//重新加密
		cache.forEach { (id, secret) -> encryptTo(secret, secretsDir.resolve("$id.gpg")) }
		//重建markerFile
		createMarker()
	}
	
	//删除密钥
	private fun deleteKey() =
		gpg("--batch", "--yes", "--delete-secret-and-public-key", KEY_UID)
	
	//加密
	private fun encryptTo(input: String, output: Path) =
		gpg(
			"--batch",
			"--yes",
			"--trust-model", "always",
			"-r", KEY_UID,
			"-a", "-e",
			"-o", output.toString(),
			input = input
		)
	
	//执行gpg命令，自动设置homedir，自动处理标准输入输出
	private fun gpg(vararg args: String, input: String? = null): String {
		val cmd = listOf("gpg", "--homedir", gpgHome.toString()) + args.toList()
		val proc = ProcessBuilder(cmd).start()
		if (input != null) {
			proc.outputStream.bufferedWriter().use { it.write(input) }
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
		return id
	}
	
	override fun get(id: UUID): String {
		requireUnlocked()
		
		val file = secretsDir.resolve("$id.gpg")
		require(Files.exists(file)) { "Secret not found: $id" }
		
		return gpg("--batch", "--yes", "--passphrase", password!!, "-d", file.toString())
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
	}
	// endregion
}
