package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import java.util.UUID

enum class SocialNotificationType {
    LIKE,
    COMMENT,
    FOLLOW,
    MENTION;

    companion object {
        fun byId(id: Int) = entries.getOrElse(id) { LIKE }
    }
}

/**
 * A social event notification sent to a player.
 */
data class SocialNotification(
    val id: Long,
    val targetUuid: UUID,
    val actorUuid: UUID,
    val actorName: String,
    val type: SocialNotificationType,
    val referenceId: Long = 0L,
    val message: String = "",
    val timestamp: Long,
    var read: Boolean = false
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putLong(ID_KEY, id)
        tag.putUUID(TARGET_UUID_KEY, targetUuid)
        tag.putUUID(ACTOR_UUID_KEY, actorUuid)
        tag.putString(ACTOR_NAME_KEY, actorName)
        tag.putInt(TYPE_KEY, type.ordinal)
        tag.putLong(REFERENCE_ID_KEY, referenceId)
        tag.putString(MESSAGE_KEY, message)
        tag.putLong(TIMESTAMP_KEY, timestamp)
        tag.putBoolean(READ_KEY, read)
        return tag
    }

    companion object {
        private const val ID_KEY = "id"
        private const val TARGET_UUID_KEY = "target_uuid"
        private const val ACTOR_UUID_KEY = "actor_uuid"
        private const val ACTOR_NAME_KEY = "actor_name"
        private const val TYPE_KEY = "type"
        private const val REFERENCE_ID_KEY = "reference_id"
        private const val MESSAGE_KEY = "message"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val READ_KEY = "read"

        fun fromNbt(tag: CompoundTag): SocialNotification? {
            if (!tag.hasUUID(TARGET_UUID_KEY) || !tag.hasUUID(ACTOR_UUID_KEY)) return null
            return SocialNotification(
                id = tag.getLong(ID_KEY),
                targetUuid = tag.getUUID(TARGET_UUID_KEY),
                actorUuid = tag.getUUID(ACTOR_UUID_KEY),
                actorName = tag.getString(ACTOR_NAME_KEY),
                type = SocialNotificationType.byId(tag.getInt(TYPE_KEY)),
                referenceId = tag.getLong(REFERENCE_ID_KEY),
                message = tag.getString(MESSAGE_KEY),
                timestamp = tag.getLong(TIMESTAMP_KEY),
                read = tag.getBoolean(READ_KEY)
            )
        }
    }
}
