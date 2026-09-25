package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenPatchouliBookPacket(val bookId: String) : CobblemonSmartphoneNetworkPacket<OpenPatchouliBookPacket> {
    companion object {
        val ID = smartphoneResource("open_patchouli_book")

        fun decode(buffer: RegistryFriendlyByteBuf): OpenPatchouliBookPacket {
            return OpenPatchouliBookPacket(buffer.readUtf())
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(bookId)
    }
}
