package com.nbp.cobblemon_smartphone.api

import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext

/**
 * Lets an optional storage mod bind its own remote terminal to the smartphone via
 * sneak-right-click, without the smartphone item needing to know the mod exists.
 * Return null to let other links (or the default use()) handle the interaction.
 */
interface SmartphoneStorageLink {
    val id: String
    fun tryLink(context: UseOnContext, smartphoneStack: ItemStack): InteractionResult?
}
