/*
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

package io.github.autotweaker.core.infrastructure.persist.db.secret

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.*
import io.github.autotweaker.api.types.exception.PasswordInvalidException
import io.github.autotweaker.api.types.exception.SecretStoreLockedException
import io.github.autotweaker.api.types.exception.notfound.SecretNotFoundException
import io.github.autotweaker.core.domain.port.SecretStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DatabaseStore
import io.github.autotweaker.core.infrastructure.persist.db.base.DbStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.*
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * 对用户输入的密码进行派生，得到DK，保存派生使用的盐
 * 再用SecureRandom生成一个随机数，作为加密密钥（主密钥）
 * 主密钥常驻于内存，有被读取的风险，但与用户输入的密码没有任何关联
 * 用DK加密主密钥并落盘，要解密密文就必须先解密主密钥
 * 解密主密钥必须要DK，派生DK需要盐（明文）以及用户密码
 * 最终能够在使用用户密码保护secret的同时保护用户的密码原文
 */
class SecretManager(store: DatabaseStore) : SecretStore, Loggable, Traceable,
	DbStore(store, "Secrets", SecretTable, SecretKeyTable) {
	private val lock = ReentrantMutex()
	private val random = SecureRandom()
	
	// 对称加密的真正密钥（明文），生成自随机数
	@Volatile
	private var masterKey: ByteArray? = null
	
	// 密码是否为空，由于密码文本并不常驻内存，只能手动维护boolean
	@Volatile
	private var _isPasswordEmpty: Boolean? = null
	val isPasswordEmpty get() = _isPasswordEmpty ?: throw SecretStoreLockedException()
	
	// 密钥库是否已解锁，StateFlow可令外部省去轮询
	private val _isUnlocked = MutableStateFlow(false)
	val isUnlocked = _isUnlocked.asStateFlow()
	
	// 开机时尝试用空密码解锁，不设密码时就可以自动解锁（虽然不推荐）。同时首次启动也会使用空字符串派生DK
	suspend fun init() = trace.catching {
		unlock("")
	}.recoverException { _: PasswordInvalidException ->
		log.info("Skipped SecretManager auto-unlock  reason=password-required")
	}.getOrThrow()
	
	// 解密主密钥并保存到内存
	suspend fun unlock(password: String) = lock.withLock {
		if (masterKey != null) return@withLock // 已经解锁
		val keyRecord = readKeyRecord() // 读取主密钥、盐
		val mk = if (keyRecord == null) {
			createMasterKey(password) // 没读到，创建主密钥，派生密码，保存
		} else {
			loadMasterKey(password, keyRecord) // 读到了，尝试解密
		}
		_isPasswordEmpty = password.isEmpty()
		masterKey = mk // 保存解密后的主密钥
		_isUnlocked.value = true
		log.info("Unlocked SecretManager  hasPassword={}", password.isNotEmpty())
	}
	
	// 更改密码，会对主密钥重新加密，但不需要重新加密所有secret
	suspend fun changePassword(oldPassword: String, newPassword: String) = lock.withLock {
		val mk = getMasterKey() // 确保已解锁
		val keyRecord = readKeyRecord() ?: unreachable("Secret key record is missing while unlocked")
		val oldDk = deriveKey(oldPassword, keyRecord.salt) // 用旧盐派生旧密码
		trace.catching { decrypt(oldDk, keyRecord.encryptedMasterKey) } // 用旧DK解密旧密钥
			.also { oldDk.fill(0) } // 从内存中清除旧的DK
			.getOrElse { throw PasswordInvalidException() } // 解密失败就是密码错误，否则密码正确，开始改密码
			.fill(0) // 密码正确，但旧的主密钥我们不需要，清除掉
		val newSalt = ByteArray(SALT_LENGTH).also(random::nextBytes) // 生成新的盐
		val newDk = deriveKey(newPassword, newSalt) // 用新盐派生新密码
		withContext(NonCancellable) {
			writeKeyRecord(newSalt, encrypt(newDk, mk)) // 用新DK重新加密主密钥
			_isPasswordEmpty = newPassword.isEmpty() // 更新flag
		}
		newDk.fill(0) // 新的DK已经使用完，从内存中清除
		log.info("Changed password")
	}
	
	override suspend fun set(secret: String, id: UUID) = lock.withLock {
		val plaintext = secret.toByteArray()
		// 使用主密钥加密，需要已解锁
		val encrypted = encrypt(getMasterKey(), plaintext, id.toByteArray())
		plaintext.fill(0) // 清空二进制副本
		db.transaction {
			SecretTable.upsert { // 插入条目
				it[SecretTable.id] = id
				it[SecretTable.nonce] = encrypted.nonce
				it[SecretTable.content] = ExposedBlob(encrypted.payload)
			}
		}
		log.debug("Added secret  id={}", id)
	}
	
	override suspend fun get(id: UUID): String = lock.withLock {
		val mk = getMasterKey() // 确保已解锁
		val encrypted = db.transaction {
			SecretTable.selectAll().where { SecretTable.id eq id }.singleOrNull() // 找条目
				?.let { EncryptedData(it[SecretTable.nonce], it[SecretTable.content].bytes) }
		} ?: throw SecretNotFoundException(id) // 找不到
		return@withLock trace.catching {
			val plaintext = decrypt(mk, encrypted, id.toByteArray()) // 尝试使用主密钥解密
			String(plaintext).also { plaintext.fill(0) } // 构造出String就清除原来的二进制明文
		}.getOrElse { e ->
			log.error("Failed secret decryption  id={}", id, e)
			throw SecretNotFoundException(id) // 解密失败，应该是损坏了，报成notfound
		}.andLog(log) { debug("Retrieved secret  id={}", id) }
	}
	
	override suspend fun list(): List<UUID> = lock.withLock {
		getMasterKey() // 确保已解锁
		db.transaction {
			SecretTable.selectAll().map {
				it[SecretTable.id]
			}
		}
	}
	
	override suspend fun remove(id: UUID): Boolean = lock.withLock {
		getMasterKey() // 确保已解锁
		db.transaction {
			SecretTable.deleteWhere { SecretTable.id eq id } > 0 // 返回是否删除成功
		}.andLog(log) {
			if (it) debug("Removed secret  id={}", id)
		}
	}
	
	override fun requireUnlocked() {
		getMasterKey() // 未解锁抛出SecretStoreLockedException
	}
	
	// 生成主密钥（随机数），并使用password的派生密钥对其加密
	private suspend fun createMasterKey(password: String): ByteArray {
		val salt = ByteArray(SALT_LENGTH).also(random::nextBytes) // 生成密钥派生使用的盐
		val mk = ByteArray(KEY_LENGTH).also(random::nextBytes) // 生成主密钥
		val dk = deriveKey(password, salt) // 密钥派生，得到DK
		writeKeyRecord(salt, encrypt(dk, mk)) // 使用DK对主密钥进行加密，并保存
		dk.fill(0) // 使用完成，从内存中清除DK
		log.info("Generated master key")
		return mk // 返回主密钥
	}
	
	// 解密主密钥，使用password派生的DK（同上）
	private fun loadMasterKey(password: String, keyRecord: SecretKeyRecord): ByteArray {
		val dk = deriveKey(password, keyRecord.salt) // 密钥派生，使用与之前相同的盐
		return trace.catching { decrypt(dk, keyRecord.encryptedMasterKey) } // 尝试使用DK解密
			.also { dk.fill(0) } // 从内存中清除DK
			.onFailure { log.debug("Failed to decrypt master key  reason={}", it.message) }
			.getOrElse { throw PasswordInvalidException() } // 解密失败
	}
	
	// 读取主密钥密文，以及用于派生用户密码的盐
	private suspend fun readKeyRecord(): SecretKeyRecord? = db.transaction {
		SecretKeyTable.selectAll().singleOrNull()?.let { // 此表应只有一行
			SecretKeyRecord(
				it[SecretKeyTable.salt], // 用于Argon2id进行密钥派生的盐
				EncryptedData(
					it[SecretKeyTable.nonce], // https://en.wikipedia.org/wiki/Cryptographic_nonce
					it[SecretKeyTable.wrappedMasterKey].bytes // 密文二进制
				)
			)
		}
	}
	
	// 更新主密钥密文，以及用于派生用户密码的盐
	private suspend fun writeKeyRecord(salt: ByteArray, encryptedMasterKey: EncryptedData) = db.transaction {
		SecretKeyTable.deleteAll() // 这个表只存单行，所以先清空
		SecretKeyTable.insert { // 写入新行
			it[SecretKeyTable.salt] = salt // 盐
			it[SecretKeyTable.nonce] = encryptedMasterKey.nonce // 用于主密钥的nonce
			it[SecretKeyTable.wrappedMasterKey] = ExposedBlob(encryptedMasterKey.payload) // 主密钥密文
		}
	}.discard()
	
	// 使用Argon2id算法派生密钥
	private fun deriveKey(password: String, salt: ByteArray): ByteArray {
		val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id) // 构造算法参数
			.withVersion(Argon2Parameters.ARGON2_VERSION_13) // 算法版本，Argon2 v1.3
			.withMemoryPowOfTwo(16) // 内存开销64MiB
			.withIterations(3) // 迭代3次
			.withParallelism(4) // 并行度4
			.withSalt(salt) // 使用random也就是java.security.SecureRandom生成的盐
			.build()
		val generator = Argon2BytesGenerator().apply {
			init(parameters) // 使用上述参数配置generator
		}
		val passwordBytes = password.toByteArray() // 转二进制
		return ByteArray(KEY_LENGTH).also { // DK长度32字节
			// 运行密钥派生函数
			generator.generateBytes(passwordBytes, it)
		}.also { passwordBytes.fill(0) } // 清除二进制副本，但原String仍然需要等待GC
	}
	
	// 使用32字节key对plaintext进行加密，并使用aad作为附加认证数据。算法使用AES-256-GCM
	private fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): EncryptedData {
		val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes) // 生成新的nonce
		val cipher = Cipher.getInstance("AES/GCM/NoPadding") // 获取AES-GCM实现
		cipher.init(
			Cipher.ENCRYPT_MODE, // 加密模式
			SecretKeySpec(key, "AES"), // key长32字节，对应AES-256
			GCMParameterSpec(128, nonce) // GCM参数，认证标签128位，以及nonce
		)
		aad?.let(cipher::updateAAD) // 对于secret，附加UUID
		return EncryptedData(nonce, cipher.doFinal(plaintext)) // 运行加密
	}
	
	// 使用加密时相同的key对encrypted进行解密，并校验认证标签
	private fun decrypt(key: ByteArray, encrypted: EncryptedData, aad: ByteArray? = null): ByteArray {
		val cipher = Cipher.getInstance("AES/GCM/NoPadding") // 获取AES-GCM实现
		cipher.init(
			Cipher.DECRYPT_MODE, // 解密模式
			SecretKeySpec(key, "AES"), // 密文
			GCMParameterSpec(128, encrypted.nonce) // GCM参数，nonce与加密时相同
		)
		aad?.let(cipher::updateAAD) // 使用加密时相同的AAD，也就是secret的UUID
		return cipher.doFinal(encrypted.payload) // 进行解密
	}
	
	// 获取明文主密钥或抛出SecretStoreLockedException
	private fun getMasterKey(): ByteArray = masterKey ?: throw SecretStoreLockedException()
	
	companion object {
		private const val SALT_LENGTH = 16
		private const val NONCE_LENGTH = 12
		private const val KEY_LENGTH = 32
	}
	
	private class SecretKeyRecord(
		val salt: ByteArray, // 密钥派生使用的盐
		val encryptedMasterKey: EncryptedData // 主密钥密文
	)
	
	private class EncryptedData(
		val nonce: ByteArray, // 随机数
		val payload: ByteArray // 密文
	)
}
