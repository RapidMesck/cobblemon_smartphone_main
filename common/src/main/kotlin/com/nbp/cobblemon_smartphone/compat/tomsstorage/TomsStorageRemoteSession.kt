package com.nbp.cobblemon_smartphone.compat.tomsstorage

import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Tracks which players currently have a smartphone-triggered remote Toms Storage session
 * open, and which dimension the bound terminal is in. Toms Storage's own
 * `StorageTerminalBlockEntity#canInteractWith` (and its menu's `stillValid`, which re-checks
 * it every tick the terminal UI is open) normally requires the player to be physically
 * carrying a wireless terminal item somewhere in their real inventory. A per-loader Mixin
 * (see `mixin/tomsstorage/MixinStorageTerminalBlockEntity` in `fabric`/`neoforge`) instead
 * replicates Toms Storage's own beacon-tier remote-access rules using this session's data -
 * same-dimension infinite range at `Config.wirelessTermBeaconLvl`, cross-dimension at
 * `Config.wirelessTermBeaconLvlCrossDim` - rather than granting unconditional access, so
 * nothing needs to be placed in the player's actual inventory, and the mod's own beacon
 * balancing still applies.
 */
object TomsStorageRemoteSession {
    private val active = mutableMapOf<UUID, ResourceKey<Level>>()

    @JvmStatic
    fun begin(player: ServerPlayer, dimension: ResourceKey<Level>) {
        active[player.uuid] = dimension
    }

    @JvmStatic
    fun end(player: ServerPlayer) {
        active.remove(player.uuid)
    }

    // @JvmStatic so the Java mixins (fabric/neoforge, called from a Toms-Storage-optional
    // context) can call these as TomsStorageRemoteSession.xxx(...) rather than needing the
    // Kotlin singleton's .INSTANCE accessor.
    @JvmStatic
    fun isActive(playerUuid: UUID): Boolean = active.containsKey(playerUuid)

    /** Whether the session's bound terminal is in the given dimension. */
    @JvmStatic
    fun isBoundDimension(playerUuid: UUID, dimension: ResourceKey<Level>): Boolean =
        active[playerUuid] == dimension

    @JvmStatic
    fun onLogout(player: ServerPlayer) {
        end(player)
    }

    /** Called once per server tick; closes out sessions whose terminal menu has since closed. */
    @JvmStatic
    fun tick(server: MinecraftServer) {
        if (active.isEmpty()) return
        for (uuid in active.keys.toList()) {
            val player = server.playerList.getPlayer(uuid)
            if (player == null || player.containerMenu === player.inventoryMenu) {
                active.remove(uuid)
            }
        }
    }
}
