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

package io.github.autotweaker.core.infrastructure.persist.migrate.v1

import com.google.auto.service.AutoService
import io.github.autotweaker.core.infrastructure.persist.migrate.DataMigration
import io.github.autotweaker.core.infrastructure.persist.migrate.v1.config.AppConfigMigrator
import io.github.autotweaker.core.infrastructure.persist.migrate.v1.session.SessionIndexRebuilder
import io.github.autotweaker.core.infrastructure.persist.migrate.v1.session.SessionMessageMigrator
import io.github.autotweaker.core.infrastructure.persist.migrate.v1.session.SessionPlanner
import io.github.autotweaker.core.infrastructure.persist.migrate.v1.session.SessionRebuilder

@AutoService(DataMigration::class)
class V1DataMigration : DataMigration {
	override val targetVersion = 1
	
	override suspend fun migrate() {
		AppConfigMigrator.migrate()
		val plan = SessionPlanner.plan() ?: return
		SessionRebuilder.prepare(plan)
		val agentTimes = SessionMessageMigrator.migrate()
		SessionRebuilder.finalize(plan, agentTimes)
		SessionRebuilder.finish()
		SessionIndexRebuilder.rebuild()
	}
}
