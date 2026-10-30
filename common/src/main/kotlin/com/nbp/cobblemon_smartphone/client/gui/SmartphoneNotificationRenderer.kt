package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.api.SmartphoneActionRegistry
import com.nbp.cobblemon_smartphone.client.social.CallState
import com.nbp.cobblemon_smartphone.client.social.SocialDmCache
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.social.CallStatus
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

object SmartphoneNotificationRenderer {
    private const val BORDER_COLOR = 0xE01E1708.toInt()
    private const val YELLOW_BG = 0xFFFACC15.toInt()
    private const val YELLOW_SHADOW = 0xFFEAB308.toInt()
    private const val EXCLAMATION_COLOR = 0xFF261A0C.toInt()

    fun hasNotifications(stack: ItemStack): Boolean {
        if (SocialDmCache.unreadTotal > 0) return true
        if (com.nbp.cobblemon_smartphone.client.social.SocialNotificationCache.unreadCount > 0) return true
        if (CallState.status == CallStatus.INCOMING) return true
        for (action in SmartphoneActionRegistry.getEnabledActions()) {
            if (action.badgeCount() > 0) return true
        }
        return false
    }

    @JvmStatic
    fun renderItemDecoration(guiGraphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        if (stack.item !is SmartphoneItem) return
        if (!hasNotifications(stack)) return

        val pose = guiGraphics.pose()
        pose.pushPose()
        // Bring to front above the item sprite and durability/energy bar
        pose.translate(0f, 0f, 200f)

        val bx = x + 10
        val by = y + 1

        // 6x6 pixel art notification badge with '!'
        // Top & bottom border
        guiGraphics.fill(bx + 1, by, bx + 5, by + 1, BORDER_COLOR)
        guiGraphics.fill(bx + 1, by + 5, bx + 5, by + 6, BORDER_COLOR)
        // Left & right border
        guiGraphics.fill(bx, by + 1, bx + 1, by + 5, BORDER_COLOR)
        guiGraphics.fill(bx + 5, by + 1, bx + 6, by + 5, BORDER_COLOR)

        // Yellow background
        guiGraphics.fill(bx + 1, by + 1, bx + 5, by + 4, YELLOW_BG)
        guiGraphics.fill(bx + 1, by + 4, bx + 5, by + 5, YELLOW_SHADOW)

        // Exclamation mark: vertical bar & dot
        guiGraphics.fill(bx + 2, by + 1, bx + 4, by + 3, EXCLAMATION_COLOR)
        guiGraphics.fill(bx + 2, by + 4, bx + 4, by + 5, EXCLAMATION_COLOR)

        pose.popPose()
    }
}
