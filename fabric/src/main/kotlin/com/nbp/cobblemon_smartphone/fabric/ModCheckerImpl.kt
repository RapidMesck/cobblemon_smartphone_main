@file:JvmName("ModCheckerImpl")
package com.nbp.cobblemon_smartphone.fabric

import com.nbp.cobblemon_smartphone.isModLoaded
import net.fabricmc.loader.api.FabricLoader

fun isModLoaded(modId: String): Boolean {
    return FabricLoader.getInstance().isModLoaded(modId)
}