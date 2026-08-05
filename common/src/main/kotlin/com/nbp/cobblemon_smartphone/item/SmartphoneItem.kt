package com.nbp.cobblemon_smartphone.item

import com.cobblemon.mod.common.CobblemonSounds
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLinkRegistry
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient
import com.nbp.cobblemon_smartphone.client.gui.SmartphoneScreen
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

    override fun appendHoverText(
        itemStack: ItemStack,
        tooltipContext: TooltipContext,
        list: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        list.add(Component.translatable("item.cobblemon_smartphone.smartphone.desc").withStyle(ChatFormatting.GRAY))

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
