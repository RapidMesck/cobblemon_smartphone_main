package com.nbp.cobblemon_smartphone.client

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption
import com.cobblemon.mod.common.client.gui.interact.wheel.Orientation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.network.packet.FuseRotomPacket
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.isRotomPhone
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f

object RotomInteractionClientHandler {
    private val FUSE_ICON = ResourceLocation.fromNamespaceAndPath(
        "cobblemon_smartphone",
        "textures/gui/interact/interact_wheel_icon_rotom_fuse.png"
    )

    fun register() {
        CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION.subscribe { event ->
            val level = Minecraft.getInstance().level ?: return@subscribe
            val player = Minecraft.getInstance().player ?: return@subscribe

            // Look for the entity in the client world
            val entity = level.getEntitiesOfClass(
                PokemonEntity::class.java,
                player.boundingBox.inflate(16.0)
            ) { it.uuid == event.pokemonID }.firstOrNull()
                ?: (level.entitiesForRendering().firstOrNull { it.uuid == event.pokemonID } as? PokemonEntity)
                ?: return@subscribe

            val pokemon = entity.pokemon
            val isRotom = pokemon.species.name.equals("rotom", ignoreCase = true) ||
                pokemon.species.resourceIdentifier.path == "rotom"
            if (!isRotom) return@subscribe

            // Only owner can fuse
            if (entity.ownerUUID != player.uuid) return@subscribe

            // Check if player has an unfused smartphone in hands or inventory
            val hasUnfusedSmartphone = player.inventory.items.any { it.item is SmartphoneItem && !it.isRotomPhone() } ||
                (player.offhandItem.item is SmartphoneItem && !player.offhandItem.isRotomPhone())

            val fuseOption = InteractWheelOption(
                iconResource = FUSE_ICON,
                secondaryIconResource = null,
                enabled = hasUnfusedSmartphone,
                tooltipText = if (hasUnfusedSmartphone) "cobblemon_smartphone.interact.fuse_rotom" else "cobblemon_smartphone.interact.fuse_rotom_no_phone",
                colour = { if (hasUnfusedSmartphone) null else Vector3f(0.5f, 0.5f, 0.5f) },
                onPress = {
                    if (hasUnfusedSmartphone) {
                        FuseRotomPacket(event.pokemonID).sendToServer()
                        Minecraft.getInstance().setScreen(null)
                    }
                }
            )

            if (!event.options.containsKey(Orientation.EAST)) {
                event.addOption(Orientation.EAST, fuseOption)
            } else {
                event.addFillingOption(fuseOption)
            }
        }
    }
}
