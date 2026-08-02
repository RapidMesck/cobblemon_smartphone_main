package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenCobblenavFishingnavPacket : CobblemonSmartphoneNetworkPacket<OpenCobblenavFishingnavPacket> {
    companion object {
        val ID = smartphoneResource("open_cobblenav_fishingnav")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenCobblenavFishingnavPacket = OpenCobblenavFishingnavPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload needed
    }
}
