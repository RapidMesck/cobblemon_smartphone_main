package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.network.packet.CobblemonSmartphoneNetworkPacket
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class OpenCraftingTablePacket : CobblemonSmartphoneNetworkPacket<OpenCraftingTablePacket> {
    companion object {
        val ID = smartphoneResource("open_crafting_table")
        fun decode(buffer: RegistryFriendlyByteBuf): OpenCraftingTablePacket = OpenCraftingTablePacket()
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {}
}
