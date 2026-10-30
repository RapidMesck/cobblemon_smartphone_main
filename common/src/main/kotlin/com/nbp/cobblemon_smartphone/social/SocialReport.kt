package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import java.util.UUID

enum class ReportTargetType { POST, COMMENT }

/**
 * Moderation report submitted by a player regarding a post or comment.
 */
data class SocialReport(
    val id: Long,
    val targetType: ReportTargetType,
    val targetId: Long,
    val reporterUuid: UUID,
    val reporterName: String,
    val targetAuthorUuid: UUID,
    val reason: String,
    val timestamp: Long
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong("id", id)
        tag.putString("type", targetType.name)
        tag.putLong("target_id", targetId)
        tag.putUUID("reporter_uuid", reporterUuid)
        tag.putString("reporter_name", reporterName)
        tag.putUUID("author_uuid", targetAuthorUuid)
        tag.putString("reason", reason)
        tag.putLong("timestamp", timestamp)
        return tag
    }

    companion object {
        fun fromNbt(tag: CompoundTag): SocialReport? {
            if (!tag.hasUUID("reporter_uuid") || !tag.hasUUID("author_uuid")) return null
            val type = runCatching { ReportTargetType.valueOf(tag.getString("type")) }.getOrDefault(ReportTargetType.POST)
            return SocialReport(
                id = tag.getLong("id"),
                targetType = type,
                targetId = tag.getLong("target_id"),
                reporterUuid = tag.getUUID("reporter_uuid"),
                reporterName = tag.getString("reporter_name"),
                targetAuthorUuid = tag.getUUID("author_uuid"),
                reason = tag.getString("reason"),
                timestamp = tag.getLong("timestamp")
            )
        }
    }
}
