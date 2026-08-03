@file:JvmName("ModCheckerImpl")
package com.nbp.cobblemon_smartphone.fabric

import com.nbp.cobblemon_smartphone.getModName
import com.nbp.cobblemon_smartphone.isModLoaded
import net.fabricmc.loader.api.FabricLoader

fun isModLoaded(modId: String): Boolean {
    return FabricLoader.getInstance().isModLoaded(modId)
}

fun getModName(modId: String): String? {
    return FabricLoader.getInstance().getModContainer(modId)
        .map { it.metadata.name }
        .orElse(null)
}