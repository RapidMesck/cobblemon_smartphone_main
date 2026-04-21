package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenCobbledollarsShopPacket : CobblemonSmartphoneNetworkPacket<OpenCobbledollarsShopPacket> {
    companion object {
        val ID = smartphoneResource("open_cobbledollars_shop")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenCobbledollarsShopPacket = OpenCobbledollarsShopPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload required.
    }
}