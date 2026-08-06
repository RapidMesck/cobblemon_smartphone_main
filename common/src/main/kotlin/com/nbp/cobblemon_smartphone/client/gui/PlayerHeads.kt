package com.nbp.cobblemon_smartphone.client.gui

import com.google.gson.JsonObject
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.PlayerSkin
import java.util.Base64
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

    /**
     * Resolves a skin for the given [uuid].
     *
     * @param skinUrl optional skin texture URL resolved by the server. When provided, the
     *   [GameProfile] is pre-populated with a `textures` property so vanilla's [SkinManager] can
     *   download and cache it immediately instead of failing with a bare profile.
     */
    fun skinFor(uuid: UUID, name: String, skinUrl: String? = null): PlayerSkin {
        val minecraft = Minecraft.getInstance()

        // Online players are authoritative and already synced — no lookup needed.
        minecraft.connection?.getPlayerInfo(uuid)?.let { return it.skin }

        val profile = if (skinUrl != null) {
            profilesWithSkin.getOrPut(uuid) {
                GameProfile(uuid, name).also {
                    it.properties.put("textures", Property("textures", encodeSkinTexture(skinUrl)))
                }
            }
        } else {
            profiles.getOrPut(uuid) { GameProfile(uuid, name) }
        }
        return minecraft.skinManager.getInsecureSkin(profile)
    }

    fun clear() {
        profiles.clear()
        profilesWithSkin.clear()
    }

    /** Separate cache for profiles that already carry a textures property. */
    private val profilesWithSkin = mutableMapOf<UUID, GameProfile>()

    private fun encodeSkinTexture(url: String): String {
        val json = JsonObject().apply {
            add("textures", JsonObject().apply {
                add("SKIN", JsonObject().apply {
                    addProperty("url", url)
                })
            })
        }
        return Base64.getEncoder().encodeToString(json.toString().toByteArray())
    }
}
