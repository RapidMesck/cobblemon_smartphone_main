@file:JvmName("ModCheckerImpl")
package com.nbp.cobblemon_smartphone.neoforge

import com.nbp.cobblemon_smartphone.getModName
import com.nbp.cobblemon_smartphone.isModLoaded
import net.neoforged.fml.ModList

fun isModLoaded(modId: String): Boolean {
    return ModList.get().isLoaded(modId)
}

fun getModName(modId: String): String? {
    return ModList.get().getModContainerById(modId)
        .map { it.modInfo.displayName }
        .orElse(null)
}
