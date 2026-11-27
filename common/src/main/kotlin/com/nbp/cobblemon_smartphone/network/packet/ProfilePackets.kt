package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
import com.nbp.cobblemon_smartphone.social.SocialTrainerSummary
import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

class RequestProfilePacket(
    val targetUuid: UUID
) : CobblemonSmartphoneNetworkPacket<RequestProfilePacket> {
    companion object {
        val ID = smartphoneResource("request_profile")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestProfilePacket(buffer.readUUID())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
    }
}

class ProfileResponsePacket(
    val profile: SocialProfileView,
    val posts: List<SocialPostView>
) : CobblemonSmartphoneNetworkPacket<ProfileResponsePacket> {
    companion object {
        val ID = smartphoneResource("profile_response")
        fun decode(buffer: RegistryFriendlyByteBuf): ProfileResponsePacket {
            val profile = SocialProfileView.decode(buffer)
            val postCount = buffer.readVarInt().coerceIn(0, 200)
            val posts = (0 until postCount).map { SocialPostView.decode(buffer) }
            return ProfileResponsePacket(profile, posts)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        profile.encode(buffer)
        buffer.writeVarInt(posts.size)
        posts.forEach { it.encode(buffer) }
    }
}

class UpdateProfilePacket(
    val bio: String,
    val favoritePartySlot: Int, // -1 for no change, 0-5 to select from party, -2 to remove favorite
    val status: String = "",
    val bannerType: String = "",
    val syncTeamAction: Int = 0, // 0 = no change, 1 = snapshot party, -1 = clear showcase
    val showTeam: Boolean = true,
    val acceptInteractions: Boolean = true,
    val dmMutualOnly: Boolean = false
) : CobblemonSmartphoneNetworkPacket<UpdateProfilePacket> {
    companion object {
        val ID = smartphoneResource("update_profile")
        fun decode(buffer: RegistryFriendlyByteBuf) = UpdateProfilePacket(
            bio = buffer.readUtf(512),
            favoritePartySlot = buffer.readVarInt(),
            status = buffer.readUtf(128),
            bannerType = buffer.readUtf(64),
            syncTeamAction = buffer.readVarInt(),
            showTeam = buffer.readBoolean(),
            acceptInteractions = buffer.readBoolean(),
            dmMutualOnly = buffer.readBoolean()
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(bio, 512)
        buffer.writeVarInt(favoritePartySlot)
        buffer.writeUtf(status, 128)
        buffer.writeUtf(bannerType, 64)
        buffer.writeVarInt(syncTeamAction)
        buffer.writeBoolean(showTeam)
        buffer.writeBoolean(acceptInteractions)
        buffer.writeBoolean(dmMutualOnly)
    }
}

class ToggleFollowPacket(
    val targetUuid: UUID
) : CobblemonSmartphoneNetworkPacket<ToggleFollowPacket> {
    companion object {
        val ID = smartphoneResource("toggle_follow")
        fun decode(buffer: RegistryFriendlyByteBuf) = ToggleFollowPacket(buffer.readUUID())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
    }
}

class PinPostPacket(
    val postId: Long
) : CobblemonSmartphoneNetworkPacket<PinPostPacket> {
    companion object {
        val ID = smartphoneResource("pin_post")
        fun decode(buffer: RegistryFriendlyByteBuf) = PinPostPacket(buffer.readVarLong())
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarLong(postId)
    }
}

class RequestProfileListPacket(
    val targetUuid: UUID,
    val listType: ListType
) : CobblemonSmartphoneNetworkPacket<RequestProfileListPacket> {
    enum class ListType {
        FOLLOWERS,
        FOLLOWING,
        DIRECTORY;

        companion object {
            fun byId(id: Int) = entries.getOrElse(id) { DIRECTORY }
        }
    }

    companion object {
        val ID = smartphoneResource("request_profile_list")
        fun decode(buffer: RegistryFriendlyByteBuf) = RequestProfileListPacket(
            targetUuid = buffer.readUUID(),
            listType = ListType.byId(buffer.readVarInt())
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
        buffer.writeVarInt(listType.ordinal)
    }
}

class ProfileListResponsePacket(
    val targetUuid: UUID,
    val listType: RequestProfileListPacket.ListType,
    val trainers: List<SocialTrainerSummary>
) : CobblemonSmartphoneNetworkPacket<ProfileListResponsePacket> {
    companion object {
        val ID = smartphoneResource("profile_list_response")
        fun decode(buffer: RegistryFriendlyByteBuf): ProfileListResponsePacket {
            val targetUuid = buffer.readUUID()
            val listType = RequestProfileListPacket.ListType.byId(buffer.readVarInt())
            val count = buffer.readVarInt().coerceIn(0, 250)
            val trainers = (0 until count).map { SocialTrainerSummary.decode(buffer) }
            return ProfileListResponsePacket(targetUuid, listType, trainers)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(targetUuid)
        buffer.writeVarInt(listType.ordinal)
        buffer.writeVarInt(trainers.size)
        trainers.forEach { it.encode(buffer) }
    }
}
