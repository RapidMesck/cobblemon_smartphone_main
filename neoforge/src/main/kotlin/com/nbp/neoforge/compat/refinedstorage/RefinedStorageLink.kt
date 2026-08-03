package com.nbp.neoforge.compat.refinedstorage

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.actions.OpenRefinedStorageAction
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLink
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemTargetBlockEntity
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext

/**
 * Binds the smartphone to a Refined Storage network via sneak-right-click, delegating the
 * actual bind to the mod's own stable NetworkItemHelper API - it writes its own
 * network-location component onto whatever stack the player is holding (our smartphone),
 * exactly like it would for a real Wireless Grid item.
 */
object RefinedStorageLink : SmartphoneStorageLink {
    override val id = OpenRefinedStorageAction.MOD_ID

    override fun tryLink(context: UseOnContext, smartphoneStack: ItemStack): InteractionResult? {
        if (!context.isSecondaryUseActive) return null

        val level = context.level
        val blockEntity = level.getBlockEntity(context.clickedPos)
        if (blockEntity !is NetworkItemTargetBlockEntity) return null

        if (level.isClientSide) return InteractionResult.CONSUME

        val player = context.player

        if (!CobblemonSmartphone.config.features.enableRefinedStorage) {
            player?.displayClientMessage(
                Component.translatable("message.nbp.refinedstorage.disabled").withColor(0xfd0100),
                true
            )
            return InteractionResult.FAIL
        }

        if (!smartphoneStack.hasUpgrade(OpenRefinedStorageAction.UPGRADE_NBT_KEY)) {
            player?.displayClientMessage(
                Component.translatable("message.nbp.refinedstorage.no_refinedstorage_upgrade").withColor(0xfd0100),
                true
            )
            return InteractionResult.FAIL
        }

        val result = RefinedStorageApi.INSTANCE.networkItemHelper.bind(context)
        if (result == InteractionResult.SUCCESS) {
            player?.displayClientMessage(Component.translatable("message.nbp.refinedstorage.bound"), true)
        }
        return result
    }
}
