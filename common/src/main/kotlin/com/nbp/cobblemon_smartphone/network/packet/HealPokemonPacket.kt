package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.network.packet.CobblemonSmartphoneNetworkPacket
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class HealPokemonPacket : CobblemonSmartphoneNetworkPacket<HealPokemonPacket> {
    companion object {
        val ID = smartphoneResource("heal_pokemon")
        fun decode(buffer: RegistryFriendlyByteBuf): HealPokemonPacket = HealPokemonPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // Nenhum dado adicional é enviado
    }
}
