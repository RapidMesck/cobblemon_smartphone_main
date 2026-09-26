package com.nbp.cobblemon_smartphone.item

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLinkRegistry
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.UseAnim
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class SmartphoneItem(private val model: SmartphoneColor) : Item(Properties().stacksTo(MAX_STACK)) {

    fun getColor(): SmartphoneColor = model

    fun getInventoryModel(): ResourceLocation {
        return model.getInventoryModelPath()
    }

    // Modelo 3D (na mão)
    fun getHandModel(): ResourceLocation {
        return model.getHandModelPath()
    }


    companion object {
        const val MAX_STACK = 1
        const val BASE_REGISTRY_KEY = "_smartphone"
        const val TRANSLATION_KEY = "item.cobblemon_smartphone."
        const val BASE_TOOLTIP_TRANSLATION_KEY = "item.cobblemon_smartphone."
    }

    override fun use(
        level: Level,
        player: Player,
        interactionHand: InteractionHand
    ): InteractionResultHolder<ItemStack> {
        if (level.isClientSide()) {
            val stack = player.getItemInHand(interactionHand)
            // While Social is using the world as a camera view, right-click is the
            // shutter button. Do not reopen the smartphone screen from the held item.
            if (SocialPhotoClient.isCameraActive()) {
                return InteractionResultHolder.success(stack)
            }
            Minecraft.getInstance().setScreen(SmartphoneScreen(model, stack))
            player.playSound(CobblemonSounds.POKEDEX_OPEN, 0.5f, 1f)
        }
        return InteractionResultHolder.success(player.getItemInHand(interactionHand))
    }

    // Lets optional storage-mod integrations (e.g. Toms Storage) bind their own remote
    // terminal to this smartphone on sneak-right-click, without this item knowing they exist.
    override fun useOn(context: UseOnContext): InteractionResult {
        val stack = context.itemInHand
        for (link in SmartphoneStorageLinkRegistry.getAll()) {
            val result = link.tryLink(context, stack)
            if (result != null) return result
        }
        return InteractionResult.PASS
    }

    override fun getDescriptionId(itemStack: ItemStack): String {
        return "$TRANSLATION_KEY${model.modelName}_smartphone"
    }

    override fun isBarVisible(stack: ItemStack): Boolean {
        if (!CobblemonSmartphone.config.energy.enableEnergy) return false
        val max = stack.getMaxEnergy()
        return max > 0 && stack.getEnergy() < max
    }

    override fun getBarWidth(stack: ItemStack): Int {
        val max = stack.getMaxEnergy()
        if (max <= 0) return 0
        return Math.round(13.0f * stack.getEnergy() / max).coerceIn(0, 13)
    }

    override fun getBarColor(stack: ItemStack): Int {
        val max = stack.getMaxEnergy()
        if (max <= 0) return 0x22C55E
        val ratio = stack.getEnergy().toFloat() / max
        return when {
            ratio > 0.5f -> 0x22C55E
            ratio > 0.2f -> 0xEAB308
            else -> 0xEF4444
        }
    }

    override fun appendHoverText(
        itemStack: ItemStack,
        tooltipContext: TooltipContext,
        list: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        list.add(Component.translatable("item.cobblemon_smartphone.smartphone.desc").withStyle(ChatFormatting.GRAY))

        if (CobblemonSmartphone.config.energy.enableEnergy) {
            val current = itemStack.getEnergy()
            val max = itemStack.getMaxEnergy()
            val ratio = if (max > 0) current.toFloat() / max else 0f
            val color = when {
                ratio > 0.5f -> ChatFormatting.GREEN
                ratio > 0.2f -> ChatFormatting.YELLOW
                else -> ChatFormatting.RED
            }
            list.add(
                Component.translatable(
                    "tooltip.cobblemon_smartphone.energy",
                    "%,d".format(current),
                    "%,d".format(max)
                ).withStyle(color)
            )
        }

        // Show installed upgrades
        val upgrades = SmartphoneUpgradeRegistry.getInstalledUpgrades(itemStack)
        for (upgrade in upgrades) {
            val name = upgrade.displayName ?: Component.translatable(
                "upgrade.cobblemon_smartphone.${upgrade.id}"
            )
            list.add(name.copy().withStyle(ChatFormatting.GREEN))
        }
    }
}
