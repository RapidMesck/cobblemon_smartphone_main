package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * C2S packet requesting fresh server-evaluated placeholder and list data for [screenId].
 */
class RequestDatapackScreenDataPacket(
    val screenId: String
) : CobblemonSmartphoneNetworkPacket<RequestDatapackScreenDataPacket> {
    companion object {
        val ID = smartphoneResource("request_datapack_screen_data")
        fun decode(buffer: RegistryFriendlyByteBuf): RequestDatapackScreenDataPacket =
            RequestDatapackScreenDataPacket(buffer.readUtf(256))
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(screenId.take(256), 256)
    }
}
