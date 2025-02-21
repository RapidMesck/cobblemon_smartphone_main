package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenEnderChestPacket : CobblemonSmartphoneNetworkPacket<OpenEnderChestPacket> {
    companion object {
        val ID = smartphoneResource("open_enderchest")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenEnderChestPacket = OpenEnderChestPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // Nenhum dado adicional é enviado
    }
}
