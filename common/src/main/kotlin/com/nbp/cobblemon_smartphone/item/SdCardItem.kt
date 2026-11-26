package com.nbp.cobblemon_smartphone.item

import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import com.nbp.cobblemon_smartphone.upgrade.getSdCardUpgrade
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import kotlin.math.abs

class SdCardItem(
    properties: Properties = Properties().stacksTo(16)
) : Item(properties) {

    override fun getName(stack: ItemStack): Component {
        val upgradeKey = stack.getSdCardUpgrade() ?: return super.getName(stack)
        val upgradeLabel = resolveUpgradeLabel(upgradeKey)
        return Component.translatable("item.cobblemon_smartphone.sd_card.named", upgradeLabel)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        val upgradeKey = stack.getSdCardUpgrade()
        if (upgradeKey == null) {
            tooltipComponents.add(
                Component.translatable("tooltip.cobblemon_smartphone.sd_card.empty")
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }

        val upgradeLabel = resolveUpgradeLabel(upgradeKey)
        tooltipComponents.add(
            Component.translatable("tooltip.cobblemon_smartphone.sd_card.contains", upgradeLabel)
                .withStyle(ChatFormatting.AQUA)
        )
        tooltipComponents.add(
            Component.translatable("tooltip.cobblemon_smartphone.sd_card.usage")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }

    companion object {
        private val FALLBACK_PALETTE = intArrayOf(
            0x4FC3F7, 0x81C784, 0xFFB74D, 0xBA68C8, 0xE57373, 0x4DB6AC, 0xFFD54F, 0x7986CB
        )

        fun resolveUpgradeLabel(upgradeKey: String): Component {
            val registered = SmartphoneUpgradeRegistry.getUpgradeByNbtKey(upgradeKey)
            if (registered?.displayName != null) {
                return registered.displayName
            }
            return Component.translatableWithFallback(
                "upgrade.cobblemon_smartphone.$upgradeKey",
                upgradeKey
            )
        }

        @JvmStatic
        fun getTintColor(stack: ItemStack): Int {
            val upgradeKey = stack.getSdCardUpgrade() ?: return (0xFF000000.toInt() or 0xCFD8DC)
            val registered = SmartphoneUpgradeRegistry.getUpgradeByNbtKey(upgradeKey)
            val rgb = registered?.accentColor
                ?: FALLBACK_PALETTE[(upgradeKey.hashCode() and 0x7FFFFFFF) % FALLBACK_PALETTE.size]
            return 0xFF000000.toInt() or (rgb and 0xFFFFFF)
        }
    }
}
