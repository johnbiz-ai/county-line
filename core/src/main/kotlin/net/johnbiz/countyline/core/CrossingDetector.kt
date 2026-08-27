package net.johnbiz.countyline.core

/**
 * Immutable state of the crossing-detection state machine. Persist this (at
 * least [current]) so crossings are still detected across process death and
 * reboots.
 */
data class CrossingState(
    /** Last *confirmed* county the user was in, or `null` before the first fix. */
    val current: County? = null,
    /** A different county seen recently that has not yet cleared the hysteresis threshold. */
    val candidate: County? = null,
    /** Number of consecutive resolves that reported [candidate]. */
    val candidateStreak: Int = 0,
)

/** Outcome of feeding one resolved county into [CrossingDetector.update]. */
data class CrossingResult(
    val state: CrossingState,
    /** The county left, set only on the update that confirms a crossing. */
    val crossedFrom: County? = null,
    /** The county entered, set only on the update that confirms a crossing. */
    val crossedInto: County? = null,
) {
    /** True on exactly the update where a crossing is confirmed and should be notified. */
    val crossed: Boolean get() = crossedInto != null
}

/**
 * Debounces raw per-update county resolutions into confirmed county *crossings*.
 *
 * GPS noise near a border can make consecutive fixes flip between two counties.
 * A new county must be reported on [confirmations] consecutive resolves before a
 * crossing is emitted. Returning to the current county before that clears the
 * pending candidate. `null` resolves (open water, off-grid, no dataset match)
 * are ignored and leave the state untouched.
 *
 * The detector is a pure reducer: it holds no state itself, so it is trivially
 * testable and the caller owns persistence.
 */
class CrossingDetector(
    private val confirmations: Int = DEFAULT_CONFIRMATIONS,
) {
    init {
        require(confirmations >= 1) { "confirmations must be >= 1" }
    }

    fun update(state: CrossingState, resolved: County?): CrossingResult {
        // Ignore fixes we couldn't place; don't disturb a pending candidate.
        if (resolved == null) return CrossingResult(state)

        // First ever fix: adopt it silently, no notification.
        if (state.current == null) {
            return CrossingResult(CrossingState(current = resolved))
        }

        // Still in (or back in) the confirmed county: drop any pending candidate.
        if (resolved.fips == state.current.fips) {
            return CrossingResult(state.copy(candidate = null, candidateStreak = 0))
        }

        // A different county: extend or start the candidate streak.
        val streak = if (resolved.fips == state.candidate?.fips) state.candidateStreak + 1 else 1
        if (streak >= confirmations) {
            return CrossingResult(
                state = CrossingState(current = resolved),
                crossedFrom = state.current,
                crossedInto = resolved,
            )
        }
        return CrossingResult(state.copy(candidate = resolved, candidateStreak = streak))
    }

    companion object {
        const val DEFAULT_CONFIRMATIONS: Int = 3
    }
}
