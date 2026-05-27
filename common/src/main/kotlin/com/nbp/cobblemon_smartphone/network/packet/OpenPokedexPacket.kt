package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenPokedexPacket : CobblemonSmartphoneNetworkPacket<OpenPokedexPacket> {
    companion object {
        val ID = smartphoneResource("open_pokedex")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenPokedexPacket = OpenPokedexPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }
}
