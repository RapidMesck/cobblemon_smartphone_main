package com.nbp.cobblemon_smartphone.rotom

import com.nbp.cobblemon_smartphone.network.packet.DefuseRotomPacket
import com.nbp.cobblemon_smartphone.network.packet.FuseRotomPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.ROTOM_DATA_TAG
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.clearFusedPokemon
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomPhone
import io.netty.buffer.Unpooled
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class RotomFuseDefuseTest {

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
    fun testFuseRotomPacketEncodeDecode() {
        val testUuid = UUID.randomUUID()
        val packet = FuseRotomPacket(testUuid)
        val buf = createBuf()
        packet.encode(buf)

        val decoded = FuseRotomPacket.decode(buf)
        assertEquals(testUuid, decoded.entityUuid)
    }

    @Test
    fun testDefuseRotomPacketEncodeDecode() {
        val packet = DefuseRotomPacket()
        val buf = createBuf()
        packet.encode(buf)

        val decoded = DefuseRotomPacket.decode(buf)
        assertNotNull(decoded)
    }

    @Test
    fun testSmartphoneClearFusedPokemon() {
        val stack = ItemStack(Items.STICK)
        stack.setRotomPhone(true)
        stack.setRotomAccessory("bow")

        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
        tag.putString(ROTOM_DATA_TAG, "mock_pokemon_data")
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))

        val dataBefore = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        assertNotNull(dataBefore)
        assertTrue(dataBefore!!.contains(ROTOM_DATA_TAG))

        // Execute clearFusedPokemon
        stack.clearFusedPokemon()

        val dataAfter = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        assertNotNull(dataAfter)
        assertFalse(dataAfter!!.contains(ROTOM_DATA_TAG))
        assertFalse(stack.isRotomPhone())
    }
}
