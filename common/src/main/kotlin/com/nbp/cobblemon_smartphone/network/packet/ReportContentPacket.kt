package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.ReportTargetType
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf

class ReportContentPacket(
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: String
) : CobblemonSmartphoneNetworkPacket<ReportContentPacket> {
    companion object {
        val ID = smartphoneResource("report_content")
        fun decode(buffer: RegistryFriendlyByteBuf): ReportContentPacket {
            val type = runCatching { ReportTargetType.valueOf(buffer.readUtf(32)) }.getOrDefault(ReportTargetType.POST)
            val targetId = buffer.readVarLong()
            val reason = buffer.readUtf(256)
            return ReportContentPacket(type, targetId, reason)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(targetType.name)
        buffer.writeVarLong(targetId)
        buffer.writeUtf(reason, 256)
    }
}
