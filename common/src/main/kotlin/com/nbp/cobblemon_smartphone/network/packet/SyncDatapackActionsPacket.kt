package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncDatapackActionsPacket(val actions: List<SyncedActionData>) : CobblemonSmartphoneNetworkPacket<SyncDatapackActionsPacket> {
    companion object {
        val ID = smartphoneResource("sync_datapack_actions")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncDatapackActionsPacket {
            val count = buffer.readVarInt()
            val actions = (0 until count).map {
                SyncedActionData(
                    id = buffer.readUtf(),
                    texture = buffer.readUtf(),
                    hoverTexture = buffer.readUtf()
                )
            }
            return SyncDatapackActionsPacket(actions)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(actions.size)
        for (action in actions) {
            buffer.writeUtf(action.id)
            buffer.writeUtf(action.texture)
            buffer.writeUtf(action.hoverTexture)
        }
    }
}

data class SyncedActionData(
    val id: String,
    val texture: String,
    val hoverTexture: String
)
