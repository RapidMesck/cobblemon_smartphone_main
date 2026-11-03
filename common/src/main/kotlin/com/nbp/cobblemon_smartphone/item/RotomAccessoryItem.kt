package com.nbp.cobblemon_smartphone.item

import com.nbp.cobblemon_smartphone.server.RotomServerManager
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getBowtieDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRibbonDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setAccessoryDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomAccessory
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class RotomAccessoryItem(
    val accessoryType: String,
    properties: Properties = Properties().stacksTo(16)
) : Item(properties) {

    override fun getName(stack: ItemStack): Component {
        val baseName = super.getName(stack)
        if (accessoryType == "bow") {
            val color = stack.getRibbonDyeColor() ?: DyeColor.PINK
            val colorName = Component.translatable("color.minecraft.${color.serializedName}")
            return Component.translatable("item.cobblemon_smartphone.rotom_ribbon.colored", baseName, colorName)
        } else if (accessoryType == "tie") {
            val color = stack.getBowtieDyeColor() ?: DyeColor.LIGHT_BLUE
            val colorName = Component.translatable("color.minecraft.${color.serializedName}")
            return Component.translatable("item.cobblemon_smartphone.rotom_bowtie.colored", baseName, colorName)
        }
        return baseName
    }

    override fun use(
        level: Level,
        player: Player,
        usedHand: InteractionHand
    ): InteractionResultHolder<ItemStack> {
        val heldStack = player.getItemInHand(usedHand)

        // Se estiver segurando um laço ou gravata borboleta em uma mão e um corante do Minecraft na outra, tinge o acessório diretamente
        if (accessoryType == "bow" || accessoryType == "tie") {
            val otherHand = if (usedHand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
            val otherStack = player.getItemInHand(otherHand)
            val dyeItem = otherStack.item as? DyeItem
            if (dyeItem != null) {
                val targetColor = dyeItem.dyeColor
                val defaultColor = if (accessoryType == "bow") DyeColor.PINK else DyeColor.LIGHT_BLUE
                val currentColor = if (accessoryType == "bow") (heldStack.getRibbonDyeColor() ?: defaultColor) else (heldStack.getBowtieDyeColor() ?: defaultColor)
                if (currentColor != targetColor) {
                    if (!level.isClientSide) {
                        if (heldStack.count == 1) {
                            heldStack.setAccessoryDyeColor(targetColor)
                        } else {
                            heldStack.shrink(1)
                            val dyedStack = if (accessoryType == "bow") {
                                SmartphoneHelper.createColoredRibbon(targetColor)
                            } else {
                                SmartphoneHelper.createColoredBowtie(targetColor)
                            }
                            if (!player.inventory.add(dyedStack)) {
                                player.drop(dyedStack, false)
                            }
                        }
                        if (!player.isCreative) {
                            otherStack.shrink(1)
                        }
                        level.playSound(
                            null as Player?,
                            player.blockPosition(),
                            SoundEvents.DYE_USE,
                            SoundSource.PLAYERS,
                            1.0f,
                            1.1f
                        )
                    }
                    return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide)
                }
            }
        }

        // Localizar o Rotom Phone equipado ou no inventário do jogador
        val rotomPhone = SmartphoneHelper.getEquippedRotomPhone(player)
        if (rotomPhone == null) {
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cobblemon_smartphone.no_rotom_phone_equipped")
                        .withStyle(ChatFormatting.RED),
                    true
                )
            }
            return InteractionResultHolder.fail(heldStack)
        }

        val currentAccessory = rotomPhone.getRotomAccessory()

        // Item Removedor de Gênero / Acessório
        if (accessoryType == "remove") {
            if (player.isCrouching) {
                if (!level.isClientSide && player is ServerPlayer) {
                    RotomServerManager.defuseRotom(player, rotomPhone)
                }
                return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide)
            }

            if (currentAccessory == null) {
                if (level.isClientSide) {
                    player.displayClientMessage(
                        Component.translatable("message.cobblemon_smartphone.remover_defuse_hint")
                            .withStyle(ChatFormatting.YELLOW),
                        true
                    )
                }
                return InteractionResultHolder.fail(heldStack)
            }

            if (!level.isClientSide) {
                val returned = SmartphoneHelper.createAccessoryStackFromPhone(rotomPhone)
                rotomPhone.setRotomAccessory(null)
                rotomPhone.setAccessoryDyeColor(null)
                if (returned != null) {
                    if (!player.inventory.add(returned)) {
                        player.drop(returned, false)
                    }
                }
                level.playSound(
                    null as Player?,
                    player.blockPosition(),
                    SoundEvents.SHEEP_SHEAR,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.2f
                )
                player.displayClientMessage(
                    Component.translatable("message.cobblemon_smartphone.accessory_removed")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                    true
                )
            }
            return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide)
        }

        val defaultColor = if (accessoryType == "bow") DyeColor.PINK else DyeColor.LIGHT_BLUE
        val newColor = if (accessoryType == "bow") (heldStack.getRibbonDyeColor() ?: defaultColor) else (heldStack.getBowtieDyeColor() ?: defaultColor)
        val currentAccColor = if (currentAccessory == "bow") (rotomPhone.getRibbonDyeColor() ?: DyeColor.PINK) else (rotomPhone.getBowtieDyeColor() ?: DyeColor.LIGHT_BLUE)

        if (currentAccessory == accessoryType && currentAccColor == newColor) {
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cobblemon_smartphone.already_has_accessory")
                        .withStyle(ChatFormatting.YELLOW),
                    true
                )
            }
            return InteractionResultHolder.fail(heldStack)
        }

        if (!level.isClientSide) {
            // Devolver o acessório anterior (preservando a cor anterior)
            if (currentAccessory != null) {
                val returned = SmartphoneHelper.createAccessoryStackFromPhone(rotomPhone)
                if (returned != null) {
                    if (!player.inventory.add(returned)) {
                        player.drop(returned, false)
                    }
                }
            }

            // Aplicar o novo acessório e cor no smartphone
            rotomPhone.setRotomAccessory(accessoryType)
            rotomPhone.setAccessoryDyeColor(newColor)

            if (!player.isCreative) {
                heldStack.shrink(1)
            }

            level.playSound(
                null as Player?,
                player.blockPosition(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS,
                1.0f,
                1.3f
            )

            val msgKey = if (accessoryType == "bow") {
                "message.cobblemon_smartphone.accessory_applied_bow"
            } else {
                "message.cobblemon_smartphone.accessory_applied_tie"
            }

            val color = if (accessoryType == "bow") ChatFormatting.LIGHT_PURPLE else ChatFormatting.AQUA
            player.displayClientMessage(
                Component.translatable(msgKey).withStyle(color, ChatFormatting.BOLD),
                true
            )
        }

        return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        val tooltipKey = when (accessoryType) {
            "bow" -> "tooltip.cobblemon_smartphone.rotom_ribbon.desc"
            "tie" -> "tooltip.cobblemon_smartphone.rotom_bowtie.desc"
            else -> "tooltip.cobblemon_smartphone.rotom_accessory_remover.desc"
        }
        tooltipComponents.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY))
        if (accessoryType == "bow" || accessoryType == "tie") {
            val defaultColor = if (accessoryType == "bow") DyeColor.PINK else DyeColor.LIGHT_BLUE
            val color = if (accessoryType == "bow") (stack.getRibbonDyeColor() ?: defaultColor) else (stack.getBowtieDyeColor() ?: defaultColor)
            val colorName = Component.translatable("color.minecraft.${color.serializedName}")
            val colorKey = if (accessoryType == "bow") "tooltip.cobblemon_smartphone.rotom_ribbon.color" else "tooltip.cobblemon_smartphone.rotom_bowtie.color"
            val hintKey = if (accessoryType == "bow") "tooltip.cobblemon_smartphone.rotom_ribbon.dye_hint" else "tooltip.cobblemon_smartphone.rotom_bowtie.dye_hint"
            val colorStyle = if (accessoryType == "bow") ChatFormatting.LIGHT_PURPLE else ChatFormatting.AQUA
            tooltipComponents.add(
                Component.translatable(colorKey, colorName).withStyle(colorStyle)
            )
            tooltipComponents.add(
                Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY)
            )
        }
        tooltipComponents.add(Component.translatable("tooltip.cobblemon_smartphone.rotom_accessory.usage").withStyle(ChatFormatting.DARK_GRAY))
    }
}
