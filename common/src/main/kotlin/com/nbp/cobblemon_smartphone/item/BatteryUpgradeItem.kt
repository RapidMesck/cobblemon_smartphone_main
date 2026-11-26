package com.nbp.cobblemon_smartphone.item

import com.nbp.cobblemon_smartphone.upgrade.getBatteryExtraCapacity
import com.nbp.cobblemon_smartphone.upgrade.getCustomBatteryUpgradeKey
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

class BatteryUpgradeItem(
    val tier: Int,
    val upgradeKey: String,
    val bonusCapacity: Int,
    properties: Properties = Properties().stacksTo(16)
) : Item(properties) {

    override fun getName(stack: ItemStack): Component {
        val customKey = stack.getCustomBatteryUpgradeKey()
        if (customKey != null && customKey != upgradeKey) {
            return Component.translatableWithFallback("upgrade.cobblemon_smartphone.$customKey", customKey)
        }
        return super.getName(stack)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        val effectiveBonus = stack.getBatteryExtraCapacity()
        tooltipComponents.add(
            Component.translatable(
                "tooltip.cobblemon_smartphone.battery_upgrade.bonus",
                "%,d".format(effectiveBonus)
            ).withStyle(ChatFormatting.GREEN)
        )
        tooltipComponents.add(
            Component.translatable("tooltip.cobblemon_smartphone.battery_upgrade.usage")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
}
