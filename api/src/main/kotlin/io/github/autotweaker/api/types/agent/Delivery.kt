package io.github.autotweaker.api.types.agent

import java.util.UUID

interface Delivery {
	suspend fun await(): UUID
}
