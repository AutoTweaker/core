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

package io.github.autotweaker.core.infrastructure.persist.db.session

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.LongSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.get
import io.github.autotweaker.api.store.MessageCache
import io.github.autotweaker.api.types.message.AgentMessage
import io.github.autotweaker.core.domain.port.SessionRepository
import io.github.autotweaker.core.infrastructure.caffeine
import io.github.autotweaker.core.infrastructure.expireAfterAccess
import io.github.autotweaker.core.infrastructure.getOrPut
import io.github.autotweaker.core.infrastructure.set
import java.util.*
import kotlin.time.Duration.Companion.seconds

class MessageCacheImpl(private val sessionRepository: SessionRepository) : MessageCache {
	private val cache = caffeine<UUID, AgentMessage> {
		maximumSize(CacheSize().get())
		expireAfterAccess(CacheExpireSeconds().get().seconds)
	}
	
	override fun get(id: UUID): AgentMessage? =
		cache.getOrPut(id) {
			sessionRepository.loadMessage(id)
		}
	
	fun put(message: AgentMessage) {
		cache[message.id] = message
	}
	
	fun remove(ids: Set<UUID>) = cache.invalidateAll(ids)
	
	suspend fun preload(ids: Set<UUID>) {
		val missing = ids - cache.asMap().keys
		if (missing.isEmpty()) return
		sessionRepository.loadMessages(missing).forEach {
			cache[it.id] = it
		}
	}
	
	@AutoService(SettingDef::class)
	class CacheSize : LongSetting(
		4096, zh(
			"会话消息内存缓存的最大条数"
		)
	)
	
	@AutoService(SettingDef::class)
	class CacheExpireSeconds : IntSetting(
		3600, zh(
			"会话消息内存缓存中，此秒数未访问的消息从缓存中淘汰"
		)
	)
}
