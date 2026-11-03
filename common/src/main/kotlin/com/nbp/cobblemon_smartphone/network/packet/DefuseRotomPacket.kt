package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class DefuseRotomPacket : CobblemonSmartphoneNetworkPacket<DefuseRotomPacket> {
    companion object {
        val ID = smartphoneResource("defuse_rotom")

        fun decode(buffer: RegistryFriendlyByteBuf): DefuseRotomPacket {
            return DefuseRotomPacket()
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }
}
