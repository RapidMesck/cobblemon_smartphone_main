package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenCobblenavPokenavPacket : CobblemonSmartphoneNetworkPacket<OpenCobblenavPokenavPacket> {
    companion object {
        val ID = smartphoneResource("open_cobblenav_pokenav")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenCobblenavPokenavPacket = OpenCobblenavPokenavPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload needed
    }
}