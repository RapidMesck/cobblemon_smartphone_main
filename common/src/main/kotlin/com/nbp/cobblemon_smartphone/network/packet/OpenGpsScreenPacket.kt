package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Server's authorization for the client to open the GPS screen. The screen itself is
 * client-side only, so the server cannot open it directly — it validates the request and signals
 * the client to proceed.
 */
class OpenGpsScreenPacket : CobblemonSmartphoneNetworkPacket<OpenGpsScreenPacket> {
    companion object {
        val ID = smartphoneResource("open_gps_screen")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenGpsScreenPacket = OpenGpsScreenPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // Nenhum dado adicional é enviado
    }
}
