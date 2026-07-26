package com.nbp.cobblemon_smartphone.client.gui

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.PlayerSkin
import java.util.UUID

/**
 * Resolves a player's skin for GUI heads, including players who are not currently online.
 *
 * `connection.getPlayerInfo(uuid)` only knows about players in the current online list, so any
 * author who logged off would otherwise have no skin at all. Falling back to [Minecraft.skinManager]
 * gives us the real skin (fetched once and cached on disk by vanilla) and, while it loads — or in
 * offline/dev environments where it never will — vanilla's own Steve/Alex default. Never returns
 * null, so callers never need a placeholder branch.
 */
object PlayerHeads {

    /** GameProfiles are cached because the skin lookup is called every frame, per visible head. */
    private val profiles = mutableMapOf<UUID, GameProfile>()

    fun skinFor(uuid: UUID, name: String): PlayerSkin {
        val minecraft = Minecraft.getInstance()

        // Online players are authoritative and already synced — no lookup needed.
        minecraft.connection?.getPlayerInfo(uuid)?.let { return it.skin }

        val profile = profiles.getOrPut(uuid) { GameProfile(uuid, name) }
        return minecraft.skinManager.getInsecureSkin(profile)
    }

    fun clear() {
        profiles.clear()
    }
}
