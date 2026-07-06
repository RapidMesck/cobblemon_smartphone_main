package com.nbp.cobblemon_smartphone.upgrade

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object SimulatedItemUse {

    /**
     * Temporarily places a synthetic ItemStack in the player's main hand,
     * executes the given use action, and restores the original hand item.
     *
     * @param player The server player
     * @param itemPredicate Matcher for finding the item in the registry by its ResourceLocation
     * @param useAction Lambda receiving the temp stack and player; performs the item-use operation
     * @return true if simulation succeeded, false if no matching item found or an exception occurred
     */
    fun simulate(
        player: ServerPlayer,
        itemPredicate: (ResourceLocation) -> Boolean,
        useAction: (ItemStack, ServerPlayer) -> Unit
    ): Boolean {
        val item = findItem(itemPredicate) ?: return false

        val original = player.mainHandItem.copy()
        val temp = ItemStack(item)

        return try {
            player.setItemInHand(InteractionHand.MAIN_HAND, temp)
            useAction(temp, player)
            true
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.warn("Simulated item use failed for {}: {}", BuiltInRegistries.ITEM.getKey(item), e.message)
            false
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, original)
        }
    }

    /**
     * Simulates using an arbitrary item, identified by its registry id, as a datapack action.
     *
     * @param player The server player
     * @param itemId Registry id of the item to simulate (e.g. "cobblemon:poke_flute")
     * @param mode "use" for right-click use ([Item.use]); "finish_using" for completion
     *   behavior ([Item.finishUsingItem]), matching how items like the Waystones warp stone open
     * @return true if the item was found and the simulation ran, false otherwise
     */
    fun useItemById(player: ServerPlayer, itemId: String, mode: String = "use"): Boolean {
        val location = ResourceLocation.tryParse(itemId)
        if (location == null) {
            CobblemonSmartphone.LOGGER.warn("Invalid item id for simulated use: {}", itemId)
            return false
        }
        if (!BuiltInRegistries.ITEM.containsKey(location)) {
            CobblemonSmartphone.LOGGER.warn("Simulated item use skipped: item '{}' not found in registry", itemId)
            return false
        }
        return simulate(player, { it == location }) { stack, p ->
            when (mode.lowercase()) {
                "finish_using" -> stack.item.finishUsingItem(stack, p.level(), p)
                else -> stack.item.use(p.level(), p, InteractionHand.MAIN_HAND)
            }
        }
    }

    /**
     * Simulates using a CobbleNav PokeNav item via smartphone upgrade.
     * Matches any cobblenav:pokenav_item_* except pokenav_item_old.
     */
    fun usePokenav(player: ServerPlayer): Boolean {
        return simulate(player, { id ->
            id.namespace == "cobblenav" &&
                id.path.startsWith("pokenav_item_") &&
                id.path != "pokenav_item_old"
        }) { stack, p ->
            stack.item.use(p.level(), p, InteractionHand.MAIN_HAND)
        }
    }

    /**
     * Simulates using a Waystones Warp Stone item via smartphone upgrade.
     * Matches any waystones:*_warp_stone item.
     */
    fun useWaystone(player: ServerPlayer): Boolean {
        return simulate(player, { id ->
            id.namespace == "waystones" && id.path.endsWith("warp_stone")
        }) { stack, p ->
            stack.item.finishUsingItem(stack, p.level(), p)
        }
    }

    private fun findItem(predicate: (ResourceLocation) -> Boolean): Item? {
        return BuiltInRegistries.ITEM.entrySet()
            .firstOrNull { (key, _) -> predicate(key.location()) }
            ?.value
    }
}
