package com.nbp.neoforge.compat.ae2

import appeng.api.ids.AEComponents
import appeng.api.implementations.blockentities.IWirelessAccessPoint
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.actions.OpenAE2Action
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLink
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import net.minecraft.core.GlobalPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext

/**
 * Binds the smartphone to an AE2 network via sneak-right-click on a Wireless Access Point.
 * Sets `AEComponents.WIRELESS_LINK_TARGET` directly on the smartphone stack - a public,
 * network-synced AE2 DataComponentType, the exact same one a real Wireless Terminal uses
 * (see `appeng.items.tools.powered.WirelessTerminalItem.LinkableHandler`).
 */
object AE2Link : SmartphoneStorageLink {
    override val id = OpenAE2Action.MOD_ID

    override fun tryLink(context: UseOnContext, smartphoneStack: ItemStack): InteractionResult? {
        if (!context.isSecondaryUseActive) return null

        val level = context.level
        val blockEntity = level.getBlockEntity(context.clickedPos)
        if (blockEntity !is IWirelessAccessPoint) return null

        if (level.isClientSide) return InteractionResult.CONSUME

        val player = context.player

        if (!CobblemonSmartphone.config.features.enableAE2) {
            player?.displayClientMessage(
                Component.translatable("message.nbp.ae2.disabled").withColor(0xfd0100),
                true
            )
            return InteractionResult.FAIL
        }

        if (!smartphoneStack.hasUpgrade(OpenAE2Action.UPGRADE_NBT_KEY)) {
            player?.displayClientMessage(
                Component.translatable("message.nbp.ae2.no_ae2_upgrade").withColor(0xfd0100),
                true
            )
            return InteractionResult.FAIL
        }

        smartphoneStack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), context.clickedPos))
        player?.displayClientMessage(Component.translatable("message.nbp.ae2.bound"), true)
        return InteractionResult.SUCCESS
    }
}
