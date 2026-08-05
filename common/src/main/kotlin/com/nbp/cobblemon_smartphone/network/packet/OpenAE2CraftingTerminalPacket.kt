package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenAE2CraftingTerminalPacket : CobblemonSmartphoneNetworkPacket<OpenAE2CraftingTerminalPacket> {
    companion object {
        val ID = smartphoneResource("open_ae2_crafting_terminal")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenAE2CraftingTerminalPacket = OpenAE2CraftingTerminalPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload required.
    }
}
