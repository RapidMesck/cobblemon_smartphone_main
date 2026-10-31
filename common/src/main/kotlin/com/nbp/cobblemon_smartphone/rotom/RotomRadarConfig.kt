package com.nbp.cobblemon_smartphone.rotom

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import java.util.UUID

data class RotomTarget(
    var id: String = UUID.randomUUID().toString(),
    var species: String = "",       // blank or "any" means any species
    var minIvPercent: Int = 0,      // 0 to 100
    var minPerfectIvs: Int = 0,     // 0 to 6
    var nature: String = "",        // blank or "any" means any nature
    var shinyOnly: Boolean = false,
    var enabled: Boolean = true
) {
    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("id", id)
        tag.putString("species", species)
        tag.putInt("minIvPercent", minIvPercent)
        tag.putInt("minPerfectIvs", minPerfectIvs)
        tag.putString("nature", nature)
        tag.putBoolean("shinyOnly", shinyOnly)
        tag.putBoolean("enabled", enabled)
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): RotomTarget {
            return RotomTarget(
                id = if (tag.contains("id")) tag.getString("id") else UUID.randomUUID().toString(),
                species = if (tag.contains("species")) tag.getString("species") else "",
                minIvPercent = tag.getInt("minIvPercent").coerceIn(0, 100),
                minPerfectIvs = tag.getInt("minPerfectIvs").coerceIn(0, 6),
                nature = if (tag.contains("nature")) tag.getString("nature") else "",
                shinyOnly = tag.getBoolean("shinyOnly"),
                enabled = if (tag.contains("enabled")) tag.getBoolean("enabled") else true
            )
        }
    }
}

data class RotomRadarConfig(
    var enabled: Boolean = true,
    var shinyAlert: Boolean = true,
    var targetAlert: Boolean = true,
    var soundAlert: Boolean = true,
    val targets: MutableList<RotomTarget> = mutableListOf()
) {
    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putBoolean("enabled", enabled)
        tag.putBoolean("shinyAlert", shinyAlert)
        tag.putBoolean("targetAlert", targetAlert)
        tag.putBoolean("soundAlert", soundAlert)

        val list = ListTag()
        for (target in targets) {
            list.add(target.toTag())
        }
        tag.put("targets", list)
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): RotomRadarConfig {
            val config = RotomRadarConfig(
                enabled = if (tag.contains("enabled")) tag.getBoolean("enabled") else true,
                shinyAlert = if (tag.contains("shinyAlert")) tag.getBoolean("shinyAlert") else true,
                targetAlert = if (tag.contains("targetAlert")) tag.getBoolean("targetAlert") else true,
                soundAlert = if (tag.contains("soundAlert")) tag.getBoolean("soundAlert") else true
            )
            if (tag.contains("targets", Tag.TAG_LIST.toInt())) {
                val list = tag.getList("targets", Tag.TAG_COMPOUND.toInt())
                for (i in 0 until list.size) {
                    config.targets.add(RotomTarget.fromTag(list.getCompound(i)))
                }
            }
            return config
        }
    }
}
