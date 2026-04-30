package io.github.autotweaker.core.agent.phase

internal sealed class PhaseResult {
	data object Continue : PhaseResult()
	data object Done : PhaseResult()
	data object Error : PhaseResult()
}
