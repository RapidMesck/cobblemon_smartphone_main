package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveActionOrderPacket(val order: List<String>) : CobblemonSmartphoneNetworkPacket<SaveActionOrderPacket> {
    companion object {
        val ID = smartphoneResource("save_action_order")
        private const val MAX_ENTRIES = 256
        private const val MAX_STRING_LENGTH = 256
        fun decode(buffer: RegistryFriendlyByteBuf): SaveActionOrderPacket {
            val count = buffer.readVarInt().coerceAtMost(MAX_ENTRIES)
            val order = (0 until count).map { buffer.readUtf(MAX_STRING_LENGTH) }
            return SaveActionOrderPacket(order)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(order.size)
        order.forEach { buffer.writeUtf(it) }
    }
}
