package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenAE2TerminalPacket : CobblemonSmartphoneNetworkPacket<OpenAE2TerminalPacket> {
    companion object {
        val ID = smartphoneResource("open_ae2_terminal")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenAE2TerminalPacket = OpenAE2TerminalPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload required.
    }
}
