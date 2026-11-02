package com.nbp.cobblemon_smartphone.network.packet

import com.nbp.cobblemon_smartphone.social.SocialPostView
import com.nbp.cobblemon_smartphone.social.SocialProfileView
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
            val postCount = buffer.readVarInt()
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
    val favoritePartySlot: Int // -1 for no change, 0-5 to select from party, -2 to remove favorite
) : CobblemonSmartphoneNetworkPacket<UpdateProfilePacket> {
    companion object {
        val ID = smartphoneResource("update_profile")
        fun decode(buffer: RegistryFriendlyByteBuf) = UpdateProfilePacket(
            buffer.readUtf(512),
            buffer.readVarInt()
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(bio, 512)
        buffer.writeVarInt(favoritePartySlot)
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
