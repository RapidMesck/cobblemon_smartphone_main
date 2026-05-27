package com.nbp.cobblemon_smartphone.client.scanner

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

object ScannerManager {
    var isActive: Boolean = false
        private set

    private var ticksActive: Int = 0
    var wantClose: Boolean = false
        private set

    val pokedexUsageContext: PokedexUsageContext
        get() = CobblemonClient.pokedexUsageContext

    fun activate(type: PokedexType = PokedexType.RED) {
        if (isActive) return
        isActive = true
        ticksActive = 0
        wantClose = false

        pokedexUsageContext.type = type
        pokedexUsageContext.transitionIntervals = PokedexUsageContext.TRANSITION_INTERVALS
    }

    fun deactivate() {
        if (!isActive) return
        wantClose = true
    }

    fun finishDeactivation() {
        isActive = false
        wantClose = false
        ticksActive = 0
        pokedexUsageContext.resetState(true)
    }

    fun tick(player: LocalPlayer) {
        if (!isActive) return

        if (wantClose) {
            finishDeactivation()
            return
        }

        ticksActive++
        pokedexUsageContext.useTick(player, ticksActive, true)

        val keyAttack = Minecraft.getInstance().options.keyAttack
        pokedexUsageContext.attackKeyHeld(keyAttack.isDown)
    }

    fun isInUse(): Boolean = isActive && !wantClose
}
