package com.nbp.cobblemon_smartphone.client.social

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.social.CallStatus
import net.minecraft.client.Minecraft

/**
 * Drives the ring and the answer/dial timeout from the client tick, so they run regardless of what
 * screen (if any) is open — the HUD overlay only renders in-world, but a ring must be heard and a
 * missed call must give up even while the player is in a menu.
 */
object CallClientTicker {
    private var lastRing = 0L

    fun tick() {
        val player = Minecraft.getInstance().player ?: return
        val other = CallState.otherUuid ?: return
        val now = System.currentTimeMillis()

        when (CallState.status) {
            CallStatus.INCOMING -> {
                if (now - lastRing >= RING_INTERVAL_MS) {
                    lastRing = now
                    player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.6f, 1.5f)
                }
            }
            CallStatus.OUTGOING, CallStatus.IN_CALL, CallStatus.IDLE -> Unit
        }
    }

    private const val RING_INTERVAL_MS = 2_500L
}
