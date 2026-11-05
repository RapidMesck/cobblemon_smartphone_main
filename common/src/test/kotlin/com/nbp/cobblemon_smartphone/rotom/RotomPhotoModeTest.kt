package com.nbp.cobblemon_smartphone.rotom

import com.nbp.cobblemon_smartphone.client.render.RotomClientManager
import com.nbp.cobblemon_smartphone.network.packet.SetSmartphoneOpenStatePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncSmartphoneOpenStatePacket
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class RotomPhotoModeTest {

    private fun createBuf(): RegistryFriendlyByteBuf {
        return RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
    }

    @Test
    fun testSetSmartphoneOpenStatePacketPhotoMode() {
        val packet = SetSmartphoneOpenStatePacket(isOpen = true, isHorizontal = false, isSelfie = true, isPhotoMode = true)
        val buf = createBuf()
        packet.encode(buf)

        val decoded = SetSmartphoneOpenStatePacket.decode(buf)
        assertTrue(decoded.isOpen)
        assertFalse(decoded.isHorizontal)
        assertTrue(decoded.isSelfie)
        assertTrue(decoded.isPhotoMode)
    }

    @Test
    fun testSyncSmartphoneOpenStatePacketPhotoMode() {
        val uuid = UUID.randomUUID()
        val packet = SyncSmartphoneOpenStatePacket(uuid, isOpen = true, isHorizontal = true, isSelfie = false, isPhotoMode = true)
        val buf = createBuf()
        packet.encode(buf)

        val decoded = SyncSmartphoneOpenStatePacket.decode(buf)
        assertEquals(uuid, decoded.playerUuid)
        assertTrue(decoded.isOpen)
        assertTrue(decoded.isHorizontal)
        assertFalse(decoded.isSelfie)
        assertTrue(decoded.isPhotoMode)
    }

    @Test
    fun testSyncSmartphoneOpenStatePacketBackwardCompatibility() {
        val uuid = UUID.randomUUID()
        // Simulate an older packet stream that only had 3 booleans (isOpen, isHorizontal, isSelfie)
        val buf = createBuf()
        buf.writeUUID(uuid)
        buf.writeBoolean(true)
        buf.writeBoolean(false)
        buf.writeBoolean(true)

        val decoded = SyncSmartphoneOpenStatePacket.decode(buf)
        assertEquals(uuid, decoded.playerUuid)
        assertTrue(decoded.isOpen)
        assertFalse(decoded.isHorizontal)
        assertTrue(decoded.isSelfie)
        assertFalse(decoded.isPhotoMode) // Defaults to false if not present
    }

    @Test
    fun testRotomClientManagerRemotePhotoModeTracking() {
        val uuid = UUID.randomUUID()
        assertFalse(RotomClientManager.isPlayerInPhotoMode(uuid))

        RotomClientManager.setRemotePlayerOpen(uuid, isOpen = true, isHorizontal = false, isSelfie = false, isPhotoMode = true)
        assertTrue(RotomClientManager.isPlayerInPhotoMode(uuid))

        RotomClientManager.setRemotePlayerOpen(uuid, isOpen = false)
        assertFalse(RotomClientManager.isPlayerInPhotoMode(uuid))
    }
}
