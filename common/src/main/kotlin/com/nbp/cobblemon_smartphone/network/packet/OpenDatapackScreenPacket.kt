package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * S2C packet instructing the client to open a specific datapack (or built-in) smartphone screen.
 */
class OpenDatapackScreenPacket(
    val screenId: String
) : CobblemonSmartphoneNetworkPacket<OpenDatapackScreenPacket> {
    companion object {
        val ID = smartphoneResource("open_datapack_screen")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenDatapackScreenPacket =
            OpenDatapackScreenPacket(buffer.readUtf(256))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(screenId.take(256), 256)
    }
}
