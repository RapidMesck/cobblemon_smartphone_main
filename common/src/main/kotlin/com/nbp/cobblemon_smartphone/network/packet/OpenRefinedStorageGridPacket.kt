package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenRefinedStorageGridPacket : CobblemonSmartphoneNetworkPacket<OpenRefinedStorageGridPacket> {
    companion object {
        val ID = smartphoneResource("open_refinedstorage_grid")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenRefinedStorageGridPacket = OpenRefinedStorageGridPacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        // No payload required.
    }
}
