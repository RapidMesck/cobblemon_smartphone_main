package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenTomsStorageTerminalPacket : CobblemonSmartphoneNetworkPacket<OpenTomsStorageTerminalPacket> {
    companion object {
        val ID = smartphoneResource("open_toms_storage_terminal")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenTomsStorageTerminalPacket = OpenTomsStorageTerminalPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload required.
    }
}
