package com.nbp.cobblemon_smartphone.compat.refinedstorage

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Bridges to Refined Storage 2's real, stable NetworkItemHelper/NetworkItemContext API.
 * Implemented per-loader (see `compat/refinedstorage/RefinedStorageAccessImpl` in
 * `fabric`/`neoforge`), since it needs the mod's real classes at compile time and those are
 * only available via each loader's own compileOnly dependency. Left null when Refined Storage
 * isn't installed - see [RefinedStorageAccessHolder].
 */
interface RefinedStorageAccess {
    fun isBound(stack: ItemStack): Boolean
    fun isReachable(player: ServerPlayer, stack: ItemStack): Boolean
    fun openGrid(player: ServerPlayer, stack: ItemStack)
}

object RefinedStorageAccessHolder {
    var instance: RefinedStorageAccess? = null
}
