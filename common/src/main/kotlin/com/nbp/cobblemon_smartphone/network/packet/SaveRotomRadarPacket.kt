package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.rotom.RotomRadarConfig
import com.nbp.cobblemon_smartphone.rotom.RotomTarget
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class SaveRotomRadarPacket(val config: RotomRadarConfig) : CobblemonSmartphoneNetworkPacket<SaveRotomRadarPacket> {
    companion object {
        val ID = smartphoneResource("save_rotom_radar")

        fun decode(buffer: RegistryFriendlyByteBuf): SaveRotomRadarPacket {
            val enabled = buffer.readBoolean()
            val shinyAlert = buffer.readBoolean()
            val targetAlert = buffer.readBoolean()
            val soundAlert = buffer.readBoolean()
            val targetsSize = buffer.readVarInt()
            val targets = mutableListOf<RotomTarget>()
            for (i in 0 until targetsSize) {
                val id = buffer.readUtf(64)
                val species = buffer.readUtf(64)
                val minIv = buffer.readVarInt()
                val min31s = buffer.readVarInt()
                val nature = buffer.readUtf(64)
                val shinyOnly = buffer.readBoolean()
                val targetEnabled = buffer.readBoolean()
                targets.add(
                    RotomTarget(
                        id = id,
                        species = species,
                        minIvPercent = minIv,
                        minPerfectIvs = min31s,
                        nature = nature,
                        shinyOnly = shinyOnly,
                        enabled = targetEnabled
                    )
                )
            }
            return SaveRotomRadarPacket(
                RotomRadarConfig(
                    enabled = enabled,
                    shinyAlert = shinyAlert,
                    targetAlert = targetAlert,
                    soundAlert = soundAlert,
                    targets = targets
                )
            )
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(config.enabled)
        buffer.writeBoolean(config.shinyAlert)
        buffer.writeBoolean(config.targetAlert)
        buffer.writeBoolean(config.soundAlert)
        buffer.writeVarInt(config.targets.size)
        for (target in config.targets) {
            buffer.writeUtf(target.id, 64)
            buffer.writeUtf(target.species, 64)
            buffer.writeVarInt(target.minIvPercent)
            buffer.writeVarInt(target.minPerfectIvs)
            buffer.writeUtf(target.nature, 64)
            buffer.writeBoolean(target.shinyOnly)
            buffer.writeBoolean(target.enabled)
        }
    }
}
