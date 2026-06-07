package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SyncDatapackActionsPacket(val actions: List<SyncedActionData>) : CobblemonSmartphoneNetworkPacket<SyncDatapackActionsPacket> {
    companion object {
        val ID = smartphoneResource("sync_datapack_actions")
        fun decode(buffer: RegistryFriendlyByteBuf): SyncDatapackActionsPacket {
            val count = buffer.readVarInt()
            val actions = (0 until count).map {
                val id = buffer.readUtf()
                val texture = buffer.readUtf()
                val hoverTexture = buffer.readUtf()
                val hasUpgrade = buffer.readBoolean()
                val requireUpgrade = if (hasUpgrade) buffer.readUtf() else null
                val hasMod = buffer.readBoolean()
                val requireMod = if (hasMod) buffer.readUtf() else null
                val cooldownSeconds = buffer.readVarInt()
                SyncedActionData(id, texture, hoverTexture, requireUpgrade, requireMod, cooldownSeconds)
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
            buffer.writeBoolean(action.requireUpgrade != null)
            if (action.requireUpgrade != null) buffer.writeUtf(action.requireUpgrade)
            buffer.writeBoolean(action.requireMod != null)
            if (action.requireMod != null) buffer.writeUtf(action.requireMod)
            buffer.writeVarInt(action.cooldownSeconds)
        }
    }
}

data class SyncedActionData(
    val id: String,
    val texture: String,
    val hoverTexture: String,
    val requireUpgrade: String? = null,
    val requireMod: String? = null,
    val cooldownSeconds: Int = 0
)
