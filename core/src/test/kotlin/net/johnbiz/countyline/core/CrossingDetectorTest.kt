package net.johnbiz.countyline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossingDetectorTest {

    private val alameda = county("06001", "Alameda")
    private val contraCosta = county("06013", "Contra Costa")
    private val santaClara = county("06085", "Santa Clara")

    private fun county(fips: String, name: String) =
        County(fips, name, "06", "California", "CA")

    private fun feed(detector: CrossingDetector, start: CrossingState, vararg fixes: County?): CrossingResult {
        var result = CrossingResult(start)
        for (fix in fixes) result = detector.update(result.state, fix)
        return result
    }

    @Test
    fun `first fix is adopted silently`() {
        val result = CrossingDetector().update(CrossingState(), alameda)
        assertFalse(result.crossed)
        assertEquals(alameda, result.state.current)
    }

    @Test
    fun `staying put never fires`() {
        val detector = CrossingDetector(confirmations = 3)
        val result = feed(detector, CrossingState(), alameda, alameda, alameda, alameda)
        assertFalse(result.crossed)
        assertEquals(alameda, result.state.current)
    }

    @Test
    fun `crossing fires only after the confirmation threshold`() {
        val detector = CrossingDetector(confirmations = 3)
        var state = CrossingState(current = alameda)

        var r = detector.update(state, contraCosta) // 1
        assertFalse(r.crossed); state = r.state
        r = detector.update(state, contraCosta)     // 2
        assertFalse(r.crossed); state = r.state
        r = detector.update(state, contraCosta)     // 3 -> confirmed
        assertTrue(r.crossed)
        assertEquals(alameda, r.crossedFrom)
        assertEquals(contraCosta, r.crossedInto)
        assertEquals(contraCosta, r.state.current)
        assertNull(r.state.candidate)
    }

    @Test
    fun `border jitter back to the current county resets the streak`() {
        val detector = CrossingDetector(confirmations = 3)
        var state = CrossingState(current = alameda)

        state = detector.update(state, contraCosta).state // streak 1
        state = detector.update(state, alameda).state      // back home -> reset
        assertEquals(0, state.candidateStreak)
        assertNull(state.candidate)

        state = detector.update(state, contraCosta).state // streak 1 again
        val r = detector.update(state, contraCosta)       // streak 2
        assertFalse(r.crossed)
    }

    @Test
    fun `switching candidate county restarts the streak`() {
        val detector = CrossingDetector(confirmations = 3)
        var state = CrossingState(current = alameda)

        state = detector.update(state, contraCosta).state // CC streak 1
        state = detector.update(state, santaClara).state  // SC streak 1 (restart)
        assertEquals(santaClara, state.candidate)
        assertEquals(1, state.candidateStreak)

        state = detector.update(state, santaClara).state  // SC streak 2
        val r = detector.update(state, santaClara)        // SC streak 3 -> confirmed
        assertTrue(r.crossed)
        assertEquals(santaClara, r.crossedInto)
    }

    @Test
    fun `null resolves are ignored and do not disturb a pending candidate`() {
        val detector = CrossingDetector(confirmations = 2)
        var state = CrossingState(current = alameda)

        state = detector.update(state, contraCosta).state // streak 1
        state = detector.update(state, null).state         // ignored
        assertEquals(contraCosta, state.candidate)
        assertEquals(1, state.candidateStreak)

        val r = detector.update(state, contraCosta)        // streak 2 -> confirmed
        assertTrue(r.crossed)
    }

    @Test
    fun `confirmations of 1 fires on the first differing fix`() {
        val detector = CrossingDetector(confirmations = 1)
        val r = detector.update(CrossingState(current = alameda), contraCosta)
        assertTrue(r.crossed)
        assertEquals(contraCosta, r.crossedInto)
    }

    @Test
    fun `two consecutive crossings both fire`() {
        val detector = CrossingDetector(confirmations = 2)
        var state = CrossingState(current = alameda)

        state = detector.update(state, contraCosta).state
        var r = detector.update(state, contraCosta)
        assertTrue(r.crossed)
        state = r.state

        state = detector.update(state, santaClara).state
        r = detector.update(state, santaClara)
        assertTrue(r.crossed)
        assertEquals(contraCosta, r.crossedFrom)
        assertEquals(santaClara, r.crossedInto)
    }

    @Test
    fun `rejects a confirmation threshold below one`() {
        try {
            CrossingDetector(confirmations = 0)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
