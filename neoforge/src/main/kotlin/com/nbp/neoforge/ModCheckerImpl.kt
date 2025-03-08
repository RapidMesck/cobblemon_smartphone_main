@file:JvmName("ModCheckerImpl")
package com.nbp.neoforge

import com.nbp.cobblemon_smartphone.isModLoaded
import net.neoforged.fml.ModList

fun isModLoaded(modId: String): Boolean {
    return ModList.get().isLoaded(modId)
}
