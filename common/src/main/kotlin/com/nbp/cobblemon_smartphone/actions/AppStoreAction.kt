package com.nbp.cobblemon_smartphone.actions

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneAction
import com.nbp.cobblemon_smartphone.client.gui.AppStoreScreen
import com.nbp.cobblemon_smartphone.item.SdCardItem
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.upgrade.getSdCardUpgrade
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object AppStoreAction : SmartphoneAction {
    override val id = "${CobblemonSmartphone.ID}:app_store"
    override val texture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/app_store.png"
    )
    override val hoverTexture = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID,
        "textures/gui/buttons/app_store_hover.png"
    )

    override fun onClick() {
        val player = Minecraft.getInstance().player ?: return
        player.playSound(CobblemonSounds.POKEDEX_CLICK, 0.5f, 1f)
        val color = SmartphoneHelper.contextColor ?: SmartphoneColor.RED
        Minecraft.getInstance().setScreen(AppStoreScreen(color, SmartphoneHelper.contextSmartphone))
    }

    override fun isEnabled(): Boolean {
        return CobblemonSmartphone.config.features.enableAppStore
    }

    override fun badgeCount(): Int {
        val player = Minecraft.getInstance().player ?: return 0
        val phone = SmartphoneHelper.getSmartphone(player) ?: return 0
        val readyKeys = HashSet<String>()
        for (stack in player.inventory.items) {
            if (stack.item is SdCardItem) {
                val key = stack.getSdCardUpgrade()
                if (key != null && !phone.hasUpgrade(key)) {
                    readyKeys.add(key)
                }
            }
        }
        val off = player.offhandItem
        if (off.item is SdCardItem) {
            val key = off.getSdCardUpgrade()
            if (key != null && !phone.hasUpgrade(key)) {
                readyKeys.add(key)
            }
        }
        return readyKeys.size
    }
}
