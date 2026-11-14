package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveSkinPacket(val skinId: String) : CobblemonSmartphoneNetworkPacket<SaveSkinPacket> {
    companion object {
        val ID = smartphoneResource("save_skin")
        private const val MAX_STRING_LENGTH = 256

        fun decode(buffer: RegistryFriendlyByteBuf): SaveSkinPacket {
            val skinId = buffer.readUtf(MAX_STRING_LENGTH)
            return SaveSkinPacket(skinId)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(skinId, MAX_STRING_LENGTH)
    }
}
