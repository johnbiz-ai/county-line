package net.johnbiz.countyline

import net.johnbiz.countyline.boot.BootReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {

    @Test
    fun `a refused foreground-service start is reported, not thrown`() {
        // Android 12+ throws ForegroundServiceStartNotAllowedException, an IllegalStateException;
        // Android 8-11 throws a plain IllegalStateException for disallowed background starts.
        val started = BootReceiver.startSafely { throw IllegalStateException("startForegroundService() not allowed") }
        assertFalse(started)
    }

    @Test
    fun `a successful start is reported`() {
        var calls = 0
        assertTrue(BootReceiver.startSafely { calls++ })
        assertTrue(calls == 1)
    }
}
