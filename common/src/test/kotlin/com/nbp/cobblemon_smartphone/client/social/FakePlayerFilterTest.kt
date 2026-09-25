package com.nbp.cobblemon_smartphone.client.social

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.UUID

class FakePlayerFilterTest {

    @Test
    fun `identifies TAB layout slot UUIDs as fake`() {
        for (slot in 0L..120L) {
            val tabUuid = UUID(0L, slot)
            assertTrue(
                FakePlayerFilter.isFakePlayer(tabUuid, "Player$slot"),
                "Slot $slot with UUID(0, $slot) should be identified as fake"
            )
        }
        assertTrue(FakePlayerFilter.isFakePlayer(UUID(0L, 0L), "NIL"))
        assertTrue(FakePlayerFilter.isFakePlayer(UUID(0L, 5000L), "Slot5000"))
    }

    @Test
    fun `identifies TAB slot names as fake`() {
        val randomUuid = UUID.randomUUID()
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, " slot_1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, " slot_15"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "|slot_1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "!slot_1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "slot_1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "_slot_1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "-slot_1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "SLOT_80"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "!01"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "!1"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "~01"))
    }

    @Test
    fun `identifies blank, short, and null names as fake`() {
        val randomUuid = UUID.randomUUID()
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, ""))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "   "))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, null))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "A"))
    }

    @Test
    fun `identifies malformed names as fake`() {
        val randomUuid = UUID.randomUUID()
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, " LeadingSpace"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "TrailingSpace "))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "Name|WithPipe"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "§aColoredName"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "%player_name%"))
        assertTrue(FakePlayerFilter.isFakePlayer(randomUuid, "[NPC] Guard"))
    }

    @Test
    fun `accepts real Java players with online UUID`() {
        val onlineUuid = UUID.randomUUID() // v4
        assertFalse(FakePlayerFilter.isFakePlayer(onlineUuid, "RapidMesck"))
        assertFalse(FakePlayerFilter.isFakePlayer(onlineUuid, "Steve"))
        assertFalse(FakePlayerFilter.isFakePlayer(onlineUuid, "alex_123"))
        assertFalse(FakePlayerFilter.isFakePlayer(onlineUuid, "_Steve_"))
        assertFalse(FakePlayerFilter.isFakePlayer(onlineUuid, "__alex__"))
    }

    @Test
    fun `accepts real players with offline UUID`() {
        val offlineUuid = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".toByteArray(StandardCharsets.UTF_8)) // v3
        assertFalse(FakePlayerFilter.isFakePlayer(offlineUuid, "Steve"))
    }

    @Test
    fun `accepts Bedrock Floodgate players`() {
        // Floodgate sets mostSignificantBits to 0 and leastSignificantBits to 64-bit Xbox XUID (> 10^14).
        val floodgateUuid = UUID(0L, 2533274790395904L)
        assertFalse(FakePlayerFilter.isFakePlayer(floodgateUuid, ".BedrockPlayer"))
        assertFalse(FakePlayerFilter.isFakePlayer(floodgateUuid, "*BedrockPlayer"))
        assertFalse(FakePlayerFilter.isFakePlayer(floodgateUuid, ".Bedrock Gamer"))
    }
}
