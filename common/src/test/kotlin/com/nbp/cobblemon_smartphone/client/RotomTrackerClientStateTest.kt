package com.nbp.cobblemon_smartphone.client

import com.nbp.cobblemon_smartphone.network.packet.RotomAlertPacket
import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RotomTrackerClientStateTest {

    @BeforeEach
    fun setup() {
        RotomTrackerClientState.reset()
        RotomRadarClientState.updateConfig(RotomRadarConfig(hudTracker = true))
    }

    @Test
    fun `registers shiny alert and tracks position`() {
        val packet = RotomAlertPacket(
            speciesName = "Gyarados",
            isShiny = true,
            ivPercent = 88,
            nature = "adamant",
            distance = 32,
            isTargetMatch = false,
            soundAlert = true,
            targetEntityId = 1234,
            targetX = 100.5,
            targetY = 64.0,
            targetZ = -200.5,
            speciesId = "cobblemon:gyarados"
        )

        RotomTrackerClientState.onAlert(packet)

        assertTrue(RotomTrackerClientState.hasActiveTarget())
        val tracked = RotomTrackerClientState.currentTracked
        assertNotNull(tracked)
        assertEquals("Gyarados", tracked?.speciesName)
        assertTrue(tracked?.isShiny == true)
        assertEquals(88, tracked?.ivPercent)
        assertEquals("adamant", tracked?.nature)
        assertEquals(32, tracked?.distance)
        assertEquals(1234, tracked?.targetEntityId)
        assertEquals(100.5, tracked?.targetX)
        assertEquals(64.0, tracked?.targetY)
        assertEquals(-200.5, tracked?.targetZ)
        assertEquals("cobblemon:gyarados", tracked?.speciesId)
        assertFalse(tracked?.reached == true)
        assertFalse(tracked?.resolved == true)
    }

    @Test
    fun `ignores alert if hudTracker config is disabled`() {
        RotomRadarClientState.updateConfig(RotomRadarConfig(hudTracker = false))

        val packet = RotomAlertPacket(
            speciesName = "Pikachu",
            isShiny = true,
            ivPercent = 50,
            nature = "jolly",
            distance = 15,
            isTargetMatch = true,
            soundAlert = false
        )

        RotomTrackerClientState.onAlert(packet)

        assertFalse(RotomTrackerClientState.hasActiveTarget())
        assertNull(RotomTrackerClientState.currentTracked)
    }

    @Test
    fun `dismiss initiates dismiss sequence`() {
        val packet = RotomAlertPacket(
            speciesName = "Ditto",
            isShiny = false,
            ivPercent = 95,
            nature = "modest",
            distance = 20,
            isTargetMatch = true,
            soundAlert = false
        )

        RotomTrackerClientState.onAlert(packet)
        assertTrue(RotomTrackerClientState.hasActiveTarget())
        assertFalse(RotomTrackerClientState.isDismissing)

        RotomTrackerClientState.dismiss()
        assertTrue(RotomTrackerClientState.isDismissing)
    }

    @Test
    fun `reset clears tracked target immediately`() {
        val packet = RotomAlertPacket(
            speciesName = "Charizard",
            isShiny = true,
            ivPercent = 100,
            nature = "timid",
            distance = 10,
            isTargetMatch = true,
            soundAlert = true
        )

        RotomTrackerClientState.onAlert(packet)
        assertTrue(RotomTrackerClientState.hasActiveTarget())

        RotomTrackerClientState.reset()
        assertFalse(RotomTrackerClientState.hasActiveTarget())
        assertNull(RotomTrackerClientState.currentTracked)
        assertFalse(RotomTrackerClientState.isDismissing)
    }
}
