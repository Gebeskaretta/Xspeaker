package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ConnectionStatus
import com.example.model.SoundPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies Xspeaker name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Xspeaker", appName)
    }

    @Test
    fun `verify sound presets exist with proper acoustic tuning`() {
        val bassPunch = SoundPreset.BASS_PUNCH
        assertEquals(12, bassPunch.bassBoostDb)
        assertTrue(bassPunch.title.contains("Bass"))

        val cinema = SoundPreset.CINEMA_SURROUND
        assertTrue(cinema.surroundEnabled)

        val vocal = SoundPreset.VOCAL_CLARITY
        assertEquals(8, vocal.trebleBoostDb)
    }

    @Test
    fun `verify connection status states`() {
        val states = ConnectionStatus.values()
        assertTrue(states.contains(ConnectionStatus.DISCONNECTED))
        assertTrue(states.contains(ConnectionStatus.DISCOVERABLE))
        assertTrue(states.contains(ConnectionStatus.CONNECTED))
    }
}
