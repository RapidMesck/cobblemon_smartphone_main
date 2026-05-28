package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ExecuteDatapackActionPacket(val actionId: String) : CobblemonSmartphoneNetworkPacket<ExecuteDatapackActionPacket> {
    companion object {
        val ID = smartphoneResource("execute_datapack_action")
        fun decode(buffer: RegistryFriendlyByteBuf): ExecuteDatapackActionPacket {
            val actionId = buffer.readUtf()
            return ExecuteDatapackActionPacket(actionId)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(actionId)
    }
}
