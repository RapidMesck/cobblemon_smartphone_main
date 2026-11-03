package com.nbp.cobblemon_smartphone.item

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.block.PokeSnackBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.ui.InteractPokemonUIPacket
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.party
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded
import com.nbp.cobblemon_smartphone.api.SmartphoneStorageLinkRegistry
import com.nbp.cobblemon_smartphone.client.SmartphoneClientItemHandler
import com.nbp.cobblemon_smartphone.energy.SmartphoneEnergyHelper
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.upgrade.SimulatedItemUse
import com.nbp.cobblemon_smartphone.upgrade.SmartphoneUpgradeRegistry
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRibbonDyeColor
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomPhone
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
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

    fun getHandModel(stack: ItemStack): ResourceLocation {
        if (stack.isRotomPhone()) {
            return model.getRotomModelPath()
        }
        return model.getHandModelPath()
    }


    companion object {
        const val MAX_STACK = 1
        const val BASE_REGISTRY_KEY = "_smartphone"
        const val TRANSLATION_KEY = "item.cobblemon_smartphone."
        const val BASE_TOOLTIP_TRANSLATION_KEY = "item.cobblemon_smartphone."

        private val showInteractionWheelMethod by lazy {
            try {
                PokemonEntity::class.java.getDeclaredMethod(
                    "showInteractionWheel",
                    ServerPlayer::class.java,
                    ItemStack::class.java
                ).apply {
                    isAccessible = true
                }
            } catch (e: Throwable) {
                null
            }
        }
    }

    override fun use(
        level: Level,
        player: Player,
        interactionHand: InteractionHand
    ): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(interactionHand)

        // Se estiver agachado e o smartphone for um Rotom Phone com acessório, remove o acessório e devolve
        if (player.isCrouching && stack.isRotomPhone() && stack.getRotomAccessory() != null) {
            if (!level.isClientSide) {
                val returnedStack = SmartphoneHelper.createAccessoryStackFromPhone(stack)
                stack.setRotomAccessory(null)
                stack.setRibbonDyeColor(null)
                if (returnedStack != null) {
                    if (!player.inventory.add(returnedStack)) {
                        player.drop(returnedStack, false)
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
                        .withStyle(ChatFormatting.YELLOW),
                    true
                )
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }

        // Se for um Rotom Phone e estiver mirando em um Pokémon dentro do raio configurado:
        if (stack.isRotomPhone()) {
            val scanDist = if (level.isClientSide) {
                SmartphoneClientItemHandler.getClientScanRadius()
            } else {
                CobblemonSmartphone.config.rotom.scanRadius
            }
            val canScanManual = if (level.isClientSide) {
                SmartphoneClientItemHandler.isClientManualScanEnabled()
            } else {
                CobblemonSmartphone.config.rotom.enableManualScanPriority
            }

            if (canScanManual) {
                val targeted = com.nbp.cobblemon_smartphone.util.RotomScannerHelper.findTargetedPokemon(player, scanDist)
                if (targeted != null && targeted.isAlive) {
                    if (level.isClientSide) {
                        SmartphoneClientItemHandler.sendScanPriorityPacket(targeted.id)
                        player.playSound(CobblemonSounds.POKEDEX_SCAN_OPEN, 0.7f, 1.2f)
                    } else if (player is ServerPlayer) {
                        com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager.setPlayerPriorityTarget(player, targeted)
                    }
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
                }
            }
        }

        if (level.isClientSide()) {
            SmartphoneClientItemHandler.handleUseOnClient(player, model, stack)
        }
        return InteractionResultHolder.success(stack)
    }

    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        interactionTarget: LivingEntity,
        usedHand: InteractionHand
    ): InteractionResult {
        if (interactionTarget !is PokemonEntity) {
            return InteractionResult.PASS
        }

        val level = player.level()
        val heldStack = player.getItemInHand(usedHand)

        // Se o smartphone for um Rotom Phone: prioriza o escaneamento deste Pokémon!
        if (heldStack.isRotomPhone() || stack.isRotomPhone()) {
            val canScanManual = if (level.isClientSide) {
                SmartphoneClientItemHandler.isClientManualScanEnabled()
            } else {
                CobblemonSmartphone.config.rotom.enableManualScanPriority
            }

            if (canScanManual && interactionTarget.isAlive) {
                if (level.isClientSide) {
                    SmartphoneClientItemHandler.sendScanPriorityPacket(interactionTarget.id)
                    player.playSound(CobblemonSounds.POKEDEX_SCAN_OPEN, 0.7f, 1.2f)
                } else if (player is ServerPlayer) {
                    com.nbp.cobblemon_smartphone.server.RotomAutoScannerManager.setPlayerPriorityTarget(player, interactionTarget)
                }
                return InteractionResult.sidedSuccess(level.isClientSide)
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        val pokemon = interactionTarget.pokemon
        val isRotom = pokemon.species.name.equals("rotom", ignoreCase = true) ||
            pokemon.species.resourceIdentifier.path == "rotom"

        if (!isRotom) {
            return InteractionResult.PASS
        }

        // Cannot interact if Rotom or player is currently in battle
        if (interactionTarget.isBattling || (player is ServerPlayer && player.isInBattle())) {
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        if (interactionTarget.ownerUUID != player.uuid) {
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.cobblemon_smartphone.rotom_not_in_party")
                        .withStyle(ChatFormatting.RED),
                    true
                )
            }
            return InteractionResult.sidedSuccess(level.isClientSide)
        }

        // Open the Pokémon interaction wheel (radial menu) so the player can choose "Fundir com o Smartphone"
        if (player is ServerPlayer) {
            try {
                if (showInteractionWheelMethod != null) {
                    showInteractionWheelMethod?.invoke(interactionTarget, player, heldStack)
                } else {
                    val canMount = interactionTarget.canSitOnShoulder() && pokemon in player.party()
                    val canHeld = !(pokemon.heldItem().isEmpty && heldStack.isEmpty)
                    val canCosmetic = !pokemon.cosmeticItem.isEmpty && heldStack.isEmpty
                    InteractPokemonUIPacket(
                        interactionTarget.uuid,
                        canMount,
                        canHeld,
                        canCosmetic,
                        false
                    ).sendToPlayer(player)
                }
            } catch (e: Throwable) {
                val canMount = interactionTarget.canSitOnShoulder() && pokemon in player.party()
                val canHeld = !(pokemon.heldItem().isEmpty && heldStack.isEmpty)
                val canCosmetic = !pokemon.cosmeticItem.isEmpty && heldStack.isEmpty
                InteractPokemonUIPacket(
                    interactionTarget.uuid,
                    canMount,
                    canHeld,
                    canCosmetic,
                    false
                ).sendToPlayer(player)
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    // Lets optional storage-mod integrations (e.g. Toms Storage) bind their own remote
    // terminal to this smartphone on sneak-right-click, without this item knowing they exist.
    override fun useOn(context: UseOnContext): InteractionResult {
        val stack = context.itemInHand

        for (link in SmartphoneStorageLinkRegistry.getAll()) {
            val result = link.tryLink(context, stack)
            if (result != null) return result
        }

        // PokéNav integration: right-clicking (or sneak-right-clicking) a placed PokéSnack block
        if (CobblemonSmartphone.config.features.enablePokenav &&
            isModLoaded("cobblenav") &&
            stack.hasUpgrade("upgrade_pokenav")
        ) {
            val clickedBlock = context.level.getBlockState(context.clickedPos).block
            val isSnack = clickedBlock is PokeSnackBlock ||
                BuiltInRegistries.BLOCK.getKey(clickedBlock).path.contains("poke_snack")
            if (isSnack || context.isSecondaryUseActive) {
                val player = context.player
                if (player is ServerPlayer && !SmartphoneEnergyHelper.checkAndConsumeEnergy(player, "cobblemon_smartphone:cobblenav_pokenav")) {
                    return InteractionResult.FAIL
                }
                val result = SimulatedItemUse.useOnPokenav(context)
                if (result != null && result.consumesAction()) {
                    return result
                }
            }
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
        if (itemStack.isRotomPhone()) {
            val accessory = itemStack.getRotomAccessory()
            val genderText = when (accessory) {
                "bow" -> Component.translatable("tooltip.cobblemon_smartphone.rotom_accessory.bow").withStyle(ChatFormatting.LIGHT_PURPLE)
                "tie" -> Component.translatable("tooltip.cobblemon_smartphone.rotom_accessory.tie").withStyle(ChatFormatting.AQUA)
                else -> null
            }
            if (genderText != null) {
                list.add(
                    Component.translatable("tooltip.cobblemon_smartphone.rotom_phone")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                        .append(Component.literal(" ("))
                        .append(genderText)
                        .append(Component.literal(")"))
                )
            } else {
                list.add(Component.translatable("tooltip.cobblemon_smartphone.rotom_phone").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            }
        }

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
