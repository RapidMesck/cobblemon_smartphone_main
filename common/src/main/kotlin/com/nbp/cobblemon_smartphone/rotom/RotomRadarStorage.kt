package com.nbp.cobblemon_smartphone.rotom

import com.nbp.cobblemon_smartphone.util.PreferencesSaver
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer

object RotomRadarStorage {
    fun read(player: ServerPlayer): RotomRadarConfig {
        val prefs = (player as? PreferencesSaver)?.`cobblemonsmartphone$getSavedPreferences`()
            ?: return RotomRadarConfig()
        if (prefs.contains(PreferencesSaver.ROTOM_RADAR_KEY, CompoundTag.TAG_COMPOUND.toInt())) {
            val tag = prefs.getCompound(PreferencesSaver.ROTOM_RADAR_KEY)
            return RotomRadarConfig.fromTag(tag)
        }
        return RotomRadarConfig()
    }

    fun write(player: ServerPlayer, config: RotomRadarConfig) {
        val prefs = (player as? PreferencesSaver)?.`cobblemonsmartphone$getSavedPreferences`() ?: return
        prefs.put(PreferencesSaver.ROTOM_RADAR_KEY, config.toTag())
    }
}
