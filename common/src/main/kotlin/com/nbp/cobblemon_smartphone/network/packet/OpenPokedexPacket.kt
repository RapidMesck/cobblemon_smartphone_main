package com.nbp.cobblemon_smartphone.network.packet

import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenPokedexPacket(val type: PokedexType) : CobblemonSmartphoneNetworkPacket<OpenPokedexPacket> {
    companion object {
        val ID = smartphoneResource("open_pokedex")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenPokedexPacket =
            OpenPokedexPacket(buffer.readEnum(PokedexType::class.java))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(type)
    }
}
