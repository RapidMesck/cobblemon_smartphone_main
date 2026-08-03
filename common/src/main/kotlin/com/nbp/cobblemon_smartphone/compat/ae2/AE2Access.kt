package com.nbp.cobblemon_smartphone.compat.ae2

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Bridges to Applied Energistics 2's real, public API (appeng.api.*). Implemented in
 * `neoforge/` only - AE2 has no Fabric build for this Minecraft version, so there's no
 * `fabric/` counterpart at all. Left null when AE2 isn't installed - see [AE2AccessHolder].
 */
interface AE2Access {
    fun isBound(stack: ItemStack): Boolean
    fun isReachable(player: ServerPlayer, stack: ItemStack): Boolean
    fun openTerminal(player: ServerPlayer, stack: ItemStack)
}

object AE2AccessHolder {
    var instance: AE2Access? = null
}
