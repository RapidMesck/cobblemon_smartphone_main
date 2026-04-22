package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class OpenWaystonesWarpStonePacket : CobblemonSmartphoneNetworkPacket<OpenWaystonesWarpStonePacket> {

    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(CobblemonSmartphone.ID, "open_waystones_warp_stone")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenWaystonesWarpStonePacket = OpenWaystonesWarpStonePacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload required.
    }
}
