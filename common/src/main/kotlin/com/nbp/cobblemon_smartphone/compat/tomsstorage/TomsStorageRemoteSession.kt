package com.nbp.cobblemon_smartphone.compat.tomsstorage

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Tracks which players currently have a smartphone-triggered remote Toms Storage session
 * open. Toms Storage's `StorageTerminalBlockEntity#canInteractWith` (and its menu's
 * `stillValid`, which re-checks it every tick the terminal UI is open) normally requires the
 * player to be physically carrying a wireless terminal item somewhere in their real
 * inventory. A per-loader Mixin (see `mixin/tomsstorage/MixinStorageTerminalBlockEntity` in
 * `fabric`/`neoforge`) short-circuits that check to true while a session is active here,
 * so nothing needs to be placed in the player's actual inventory at all.
 */
object TomsStorageRemoteSession {
    private val active = mutableSetOf<UUID>()

    @JvmStatic
    fun begin(player: ServerPlayer) {
        active.add(player.uuid)
    }

    @JvmStatic
    fun end(player: ServerPlayer) {
        active.remove(player.uuid)
    }

    // @JvmStatic so the Java mixins (fabric/neoforge, called from a Toms-Storage-optional
    // context) can call this as TomsStorageRemoteSession.isActive(...) rather than needing
    // the Kotlin singleton's .INSTANCE accessor.
    @JvmStatic
    fun isActive(playerUuid: UUID): Boolean = active.contains(playerUuid)

    @JvmStatic
    fun onLogout(player: ServerPlayer) {
        end(player)
    }

    /** Called once per server tick; closes out sessions whose terminal menu has since closed. */
    @JvmStatic
    fun tick(server: MinecraftServer) {
        if (active.isEmpty()) return
        for (uuid in active.toList()) {
            val player = server.playerList.getPlayer(uuid)
            if (player == null || player.containerMenu === player.inventoryMenu) {
                active.remove(uuid)
            }
        }
    }
}
