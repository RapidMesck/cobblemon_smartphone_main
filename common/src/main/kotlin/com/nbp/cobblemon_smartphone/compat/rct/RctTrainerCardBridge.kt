package com.nbp.cobblemon_smartphone.compat.rct

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * The single place that knows whether Radical Cobblemon Trainers (RCT) is usable and how to
 * open its Trainer Card screen.
 *
 * Nothing here references an RCT class in its signature — the screen is instantiated through
 * reflection, so this object is always safe to load, including on setups without the mod.
 */
object RctTrainerCardBridge {
    const val RCT_MOD_ID = "rctmod"
    private const val TRAINER_CARD_SCREEN_CLASS = "com.gitlab.srcmc.rctmod.client.screens.TrainerCardScreen"

    /**
     * True only when the mod is installed AND its Trainer Card screen class is present.
     * Cheap enough for the render thread: class lookup is cached after the first resolution.
     */
    val isAvailable: Boolean by lazy {
        if (!isModLoaded(RCT_MOD_ID)) {
            return@lazy false
        }
        try {
            Class.forName(TRAINER_CARD_SCREEN_CLASS)
            true
        } catch (e: ClassNotFoundException) {
            CobblemonSmartphone.LOGGER.warn("RCT mod present but TrainerCardScreen class was not found", e)
            false
        }
    }

    /** Opens the RCT Trainer Card screen on the client. Returns false if it could not be opened. */
    fun openTrainerCardScreen(): Boolean {
        if (!isAvailable) return false
        return try {
            val screenClass = Class.forName(TRAINER_CARD_SCREEN_CLASS)
            val screen = screenClass.getDeclaredConstructor().newInstance() as Screen
            Minecraft.getInstance().setScreen(screen)
            true
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error("Failed to open RCT Trainer Card screen", e)
            false
        }
    }
}
