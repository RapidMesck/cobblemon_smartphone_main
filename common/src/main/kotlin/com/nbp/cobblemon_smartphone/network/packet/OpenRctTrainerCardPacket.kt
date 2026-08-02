package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Client asks the server for permission to open the RCT Trainer Card screen. The server is the
 * authority: it validates its own config and only then replies with [OpenRctTrainerCardScreenPacket].
 */
class OpenRctTrainerCardPacket : CobblemonSmartphoneNetworkPacket<OpenRctTrainerCardPacket> {
    companion object {
        val ID = smartphoneResource("open_rct_trainer_card")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenRctTrainerCardPacket = OpenRctTrainerCardPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // Nenhum dado adicional é enviado
    }
}
