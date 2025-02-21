package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.network.packet.CobblemonSmartphoneNetworkPacket
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenPCPacket : CobblemonSmartphoneNetworkPacket<OpenPCPacket> {
    companion object {
        val ID = smartphoneResource("open_pc")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenPCPacket = OpenPCPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // Nenhum dado adicional é enviado
    }
}
