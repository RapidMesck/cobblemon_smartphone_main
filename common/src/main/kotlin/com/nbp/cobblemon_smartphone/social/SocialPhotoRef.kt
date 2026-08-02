package com.nbp.cobblemon_smartphone.social

import net.minecraft.nbt.CompoundTag
import java.util.UUID

data class SocialPhotoRef(val id: UUID, val width: Int, val height: Int) {
    fun toNbt() = CompoundTag().also {
        it.putUUID("id", id)
        it.putInt("width", width)
        it.putInt("height", height)
    }

    companion object {
        fun fromNbt(tag: CompoundTag): SocialPhotoRef? = if (tag.hasUUID("id")) {
            SocialPhotoRef(tag.getUUID("id"), tag.getInt("width"), tag.getInt("height"))
        } else null
    }
}
