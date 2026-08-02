package com.nbp.cobblemon_smartphone.compat.tomsstorage

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLink
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block

/**
 * Binds the smartphone to a Toms Storage terminal via sneak-right-click, mirroring how
 * the mod's own Advanced Wireless Terminal binds itself. Deliberately avoids depending on
 * Toms Storage's classes: the "remote_activate" block tag and the bound position are both
 * plain vanilla data, so this works whether or not the mod jar is even on the classpath -
 * [TomsStorageLink.id] via [com.nbp.cobblemon_smartphone.ModChecker.isModLoaded] is the only
 * runtime gate.
 */
object TomsStorageLink : SmartphoneStorageLink {
    const val MOD_ID = "toms_storage"
    const val UPGRADE_NBT_KEY = "upgrade_toms_storage"

    override val id = MOD_ID

    val REMOTE_ACTIVATE_TAG: TagKey<Block> =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "remote_activate"))

    override fun tryLink(context: UseOnContext, smartphoneStack: ItemStack): InteractionResult? {
        if (!context.isSecondaryUseActive) return null

        val level = context.level
        val state = level.getBlockState(context.clickedPos)
        if (!state.`is`(REMOTE_ACTIVATE_TAG)) return null

        if (level.isClientSide) return InteractionResult.CONSUME

        val player = context.player

        if (!CobblemonSmartphone.config.features.enableTomsStorage) {
            player?.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.disabled").withColor(0xfd0100),
                true
            )
            return InteractionResult.FAIL
        }

        if (!smartphoneStack.hasUpgrade(UPGRADE_NBT_KEY)) {
            player?.displayClientMessage(
                Component.translatable("message.nbp.toms_storage.no_toms_storage_upgrade").withColor(0xfd0100),
                true
            )
            return InteractionResult.FAIL
        }

        smartphoneStack.setTomsStorageLink(level.dimension(), context.clickedPos)
        player?.displayClientMessage(Component.translatable("message.nbp.toms_storage.bound"), true)
        return InteractionResult.SUCCESS
    }
}
