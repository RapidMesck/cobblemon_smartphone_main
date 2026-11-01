package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class CloseRotomDialoguePacket : CobblemonSmartphoneNetworkPacket<CloseRotomDialoguePacket> {
    companion object {
        val ID = smartphoneResource("close_rotom_dialogue")

        fun decode(buffer: RegistryFriendlyByteBuf): CloseRotomDialoguePacket {
            return CloseRotomDialoguePacket()
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }
}
