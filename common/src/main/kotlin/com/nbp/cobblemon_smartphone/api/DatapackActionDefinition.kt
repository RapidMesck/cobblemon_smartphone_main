package com.nbp.cobblemon_smartphone.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class DatapackActionDefinition(
    val id: String,
    val texture: String,
    @SerializedName("hover_texture")
    val hoverTexture: String,
    val commands: List<String?>? = emptyList(),
    val functions: List<String?>? = emptyList(),
    @SerializedName("use_items")
    val useItems: List<UseItemEntry?>? = emptyList(),
    val order: Int = 0,
    @SerializedName("require_mod")
    val requireMod: String? = null,
    @SerializedName("cooldown_seconds")
    val cooldownSeconds: Int = 0,
    @SerializedName("require_upgrade")
    val requireUpgrade: String? = null
) {
    companion object {
        val GSON = Gson()

        fun fromJson(json: String): DatapackActionDefinition? {
            return try {
                GSON.fromJson(json, DatapackActionDefinition::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * A single simulated item use for a datapack action.
 *
 * @property id Registry id of the item to simulate (e.g. "cobblemon:poke_flute").
 * @property mode "use" for right-click use ([net.minecraft.world.item.Item.use]),
 *   "finish_using" for completion behavior ([net.minecraft.world.item.Item.finishUsingItem]).
 */
data class UseItemEntry(
    val id: String,
    val mode: String = "use"
)
