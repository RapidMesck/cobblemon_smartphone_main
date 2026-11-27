package com.nbp.cobblemon_smartphone.rotom

import com.google.gson.Gson
import com.nbp.cobblemon_smartphone.config.SmartphoneConfig
import com.nbp.cobblemon_smartphone.network.packet.RotomDroneStatePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomConfigPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncRotomDroneStatePacket
import com.nbp.cobblemon_smartphone.server.RotomDroneServerManager
import io.netty.buffer.Unpooled
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class RotomDroneTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private fun createBuf(): RegistryFriendlyByteBuf {
        return RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
    }

    @Test
    fun testRotomDroneConfigDefaults() {
        val rotom = SmartphoneConfig.RotomConfig()
        assertTrue(rotom.enableDrone)
        assertEquals(32.0, rotom.droneMaxRange)
        assertEquals(0.45, rotom.droneSpeed)
        assertTrue(rotom.droneAllowPhoto)
        assertFalse(rotom.droneAllowFluids)
        assertTrue(rotom.droneBlockedDimensions.isEmpty())
        assertEquals(0, rotom.droneEnergyPerSecond)
    }

    @Test
    fun testRotomDroneConfigJsonDeserialization() {
        val gson = Gson()
        val cfg = gson.fromJson(
            """{"rotom":{"enableDrone":false,"droneMaxRange":48.0,"droneSpeed":0.6,"droneAllowPhoto":false,"droneAllowFluids":true,"droneBlockedDimensions":["minecraft:the_end","minecraft:the_nether"],"droneEnergyPerSecond":25}}""",
            SmartphoneConfig::class.java
        )
        assertFalse(cfg.rotom.enableDrone)
        assertEquals(48.0, cfg.rotom.droneMaxRange)
        assertEquals(0.6, cfg.rotom.droneSpeed)
        assertFalse(cfg.rotom.droneAllowPhoto)
        assertTrue(cfg.rotom.droneAllowFluids)
        assertEquals(listOf("minecraft:the_end", "minecraft:the_nether"), cfg.rotom.droneBlockedDimensions)
        assertEquals(25, cfg.rotom.droneEnergyPerSecond)
    }

    @Test
    fun testSyncRotomConfigPacketDroneRoundTrip() {
        val packet = SyncRotomConfigPacket(
            enableScanner = true,
            enableAutoScanner = true,
            enableManualScanPriority = true,
            enableSocialInteractions = true,
            allowScanAlreadyRegistered = true,
            requireLineOfSight = true,
            revealIvPercentage = true,
            revealNature = true,
            scanRadius = 10.0,
            scanDurationTicks = 50,
            approachTicks = 38,
            returnTicks = 28,
            infiniteEnergy = false,
            enableSocialDataShare = true,
            socialDataShareChance = 0.35,
            curiosityLanguage = "pt",
            enableRotom = true,
            enableDrone = true,
            droneMaxRange = 32.0,
            droneSpeed = 0.55,
            droneAllowPhoto = true,
            droneAllowFluids = true,
            droneBlockedDimensions = listOf("minecraft:the_end"),
            droneEnergyPerSecond = 15,
            enableSocial = true
        )
        val buf = createBuf()
        packet.encode(buf)

        val decoded = SyncRotomConfigPacket.decode(buf)
        assertTrue(decoded.enableDrone)
        assertEquals(32.0, decoded.droneMaxRange)
        assertEquals(0.55, decoded.droneSpeed)
        assertTrue(decoded.droneAllowPhoto)
        assertTrue(decoded.droneAllowFluids)
        assertEquals(listOf("minecraft:the_end"), decoded.droneBlockedDimensions)
        assertEquals(15, decoded.droneEnergyPerSecond)
        assertTrue(decoded.enableSocial)
    }

    @Test
    fun testRotomDroneStatePacketRoundTrip() {
        val activePacket = RotomDroneStatePacket(true, 10.5, 72.25, -44.75, 135.5f, -18.25f)
        val activeBuf = createBuf()
        activePacket.encode(activeBuf)
        val decodedActive = RotomDroneStatePacket.decode(activeBuf)
        assertTrue(decodedActive.active)
        assertEquals(10.5, decodedActive.x)
        assertEquals(72.25, decodedActive.y)
        assertEquals(-44.75, decodedActive.z)
        assertEquals(135.5f, decodedActive.yaw)
        assertEquals(-18.25f, decodedActive.pitch)

        val stopPacket = RotomDroneStatePacket(false)
        val stopBuf = createBuf()
        stopPacket.encode(stopBuf)
        val decodedStop = RotomDroneStatePacket.decode(stopBuf)
        assertFalse(decodedStop.active)
    }

    @Test
    fun testSyncRotomDroneStatePacketRoundTrip() {
        val uuid = UUID.randomUUID()
        val packet = SyncRotomDroneStatePacket(uuid, true, 1.0, 2.0, 3.0, 90f, 15f)
        val buf = createBuf()
        packet.encode(buf)
        val decoded = SyncRotomDroneStatePacket.decode(buf)
        assertEquals(uuid, decoded.playerUuid)
        assertTrue(decoded.active)
        assertEquals(1.0, decoded.x)
        assertEquals(2.0, decoded.y)
        assertEquals(3.0, decoded.z)
        assertEquals(90f, decoded.yaw)
        assertEquals(15f, decoded.pitch)
    }

    @Test
    fun testBlockedDimensionMatchingIsCaseInsensitive() {
        val blocked = listOf("minecraft:the_end", "custom:arena")
        assertTrue(RotomDroneServerManager.isDimensionBlocked("minecraft:the_end", blocked))
        assertTrue(RotomDroneServerManager.isDimensionBlocked("MINECRAFT:THE_END", blocked))
        assertFalse(RotomDroneServerManager.isDimensionBlocked("minecraft:overworld", blocked))
    }
}
