package com.nbp.cobblemon_smartphone.client.keybind

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.actions.PokedexAction
import com.nbp.cobblemon_smartphone.api.QuickActionBindings
import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Resolves and fires the smartphone app bound to a Quick Action hotkey slot, without requiring
 * the smartphone screen to be open. Mirrors the context [SmartphoneScreen.init] normally sets up
 * (contextSmartphone/contextColor/PokedexAction.requestedPokedexType) since several actions'
 * onClick()/isEnabled() implementations depend on it.
 */
object QuickActionDispatcher {
    private const val ERROR_COLOR = 0xfd0100

    fun trigger(slot: Int) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.screen != null) return
        if (!CobblemonSmartphone.config.features.enableQuickActions) return

        val actionId = QuickActionBindings.actionIdFor(slot)
        if (actionId == null) {
            player.displayClientMessage(
                Component.translatable("message.nbp.quick_action.unassigned").withColor(ERROR_COLOR),
                true
            )
            return
        }

        val smartphoneStack = SmartphoneHelper.getSmartphone(player)
        if (smartphoneStack == null) {
            player.displayClientMessage(
                Component.translatable("message.nbp.quick_action.no_phone").withColor(ERROR_COLOR),
                true
            )
            return
        }

        val color = (smartphoneStack.item as? SmartphoneItem)?.getColor()
        SmartphoneHelper.contextSmartphone = smartphoneStack
        SmartphoneHelper.contextColor = color
        if (color != null) {
            PokedexAction.requestedPokedexType = color.toPokedexType()
        }

        try {
            val action = SmartphoneActionRegistry.getEnabledActions().firstOrNull { it.id == actionId }
            if (action == null) {
                player.displayClientMessage(
                    Component.translatable("message.nbp.quick_action.unavailable").withColor(ERROR_COLOR),
                    true
                )
                return
            }
            action.onClick()
        } finally {
            SmartphoneHelper.contextSmartphone = null
            SmartphoneHelper.contextColor = null
        }
    }
}
