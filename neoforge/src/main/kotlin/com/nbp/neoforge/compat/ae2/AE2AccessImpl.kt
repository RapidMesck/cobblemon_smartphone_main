package com.nbp.neoforge.compat.ae2

import appeng.api.config.Actionable
import appeng.api.ids.AEComponents
import appeng.items.tools.powered.WirelessTerminalItem
import appeng.items.tools.powered.WirelessCraftingTerminalItem
import appeng.menu.MenuOpener
import appeng.menu.locator.ItemMenuHostLocator
import appeng.menu.locator.MenuLocators
import com.nbp.cobblemon_smartphone.compat.ae2.AE2Access
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult

/**
 * `WirelessTerminalItem`'s real opening flow (`use`/`openFromInventory`) requires the held
 * stack's item to literally be a `WirelessTerminalItem` and reads/writes power through a real
 * `ItemMenuHostLocator`. Rather than reflection or a throwaway `Item` instance (unsafe
 * post-bootstrap - see the Refined Storage integration notes), this builds an in-memory,
 * always-freshly-charged `ItemStack` (via the real registered item's own public
 * `injectAEPower`), carrying the same `WIRELESS_LINK_TARGET` component our smartphone is bound
 * to, and opens the menu through [SyntheticWirelessLocator] - a real [ItemMenuHostLocator]
 * registered via AE2's own public `MenuLocators.register` extension point (never placed in the
 * player's real inventory, so nothing is visible). `item.getMenuType()` is called
 * polymorphically rather than hardcoding `MEStorageMenu.WIRELESS_TYPE`, so this still opens
 * whatever menu the actually-registered `ae2:wireless_terminal` item reports (some modpacks
 * replace it via a companion mod).
 *
 * Range is intentionally NOT bypassed: `getLinkedGrid` only checks that the bound Wireless
 * Access Point still exists and belongs to a network - same as what a real Wireless Terminal's
 * `use()` allows before opening. Actual distance-to-WAP validity is then re-evaluated every
 * tick by AE2's own `WirelessTerminalMenuHost`, exactly as it would for a physical terminal.
 */
object AE2AccessImpl : AE2Access {
    override fun initializeClient() {
        SyntheticWirelessLocatorRegistration.registerIfAbsent()
    }

    private fun wirelessTerminalItem(): WirelessTerminalItem? {
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", "wireless_terminal"))
        return item as? WirelessTerminalItem
    }

    override fun isBound(stack: ItemStack): Boolean =
        stack.has(AEComponents.WIRELESS_LINK_TARGET)

    override fun isReachable(player: ServerPlayer, stack: ItemStack): Boolean {
        val item = wirelessTerminalItem() ?: return false
        return item.getLinkedGrid(stack, player.level(), null) != null
    }

    override fun openTerminal(player: ServerPlayer, stack: ItemStack) {
        val item = wirelessTerminalItem() ?: return
        SyntheticWirelessLocatorRegistration.registerIfAbsent()

        val chargedStack = ItemStack(item)
        item.injectAEPower(chargedStack, item.getAEMaxPower(chargedStack), Actionable.MODULATE)
        stack.get(AEComponents.WIRELESS_LINK_TARGET)?.let {
            chargedStack.set(AEComponents.WIRELESS_LINK_TARGET, it)
        }

        MenuOpener.open(item.getMenuType(), player, SyntheticWirelessLocator(chargedStack))
    }

    override fun openCraftingTerminal(player: ServerPlayer, stack: ItemStack) {
        val item = wirelessCraftingTerminalItem() ?: return
        SyntheticWirelessLocatorRegistration.registerIfAbsent()

        val chargedStack = ItemStack(item)
        item.injectAEPower(chargedStack, item.getAEMaxPower(chargedStack), Actionable.MODULATE)
        stack.get(AEComponents.WIRELESS_LINK_TARGET)?.let {
            chargedStack.set(AEComponents.WIRELESS_LINK_TARGET, it)
        }

        MenuOpener.open(item.getMenuType(), player, SyntheticWirelessCraftingLocator(chargedStack))
    }

    private fun wirelessCraftingTerminalItem(): WirelessCraftingTerminalItem? {
        val item = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("ae2", "wireless_crafting_terminal")
        )
        return item as? WirelessCraftingTerminalItem
    }

    /** Resolves to an in-memory-only stack, never a real inventory slot - see class doc. */
    class SyntheticWirelessLocator(private val stack: ItemStack) : ItemMenuHostLocator {
        override fun locateItem(player: Player): ItemStack = stack
        override fun hitResult(): BlockHitResult? = null
    }

    class SyntheticWirelessCraftingLocator(private val stack: ItemStack) : ItemMenuHostLocator {
        override fun locateItem(player: Player): ItemStack = stack
        override fun hitResult(): BlockHitResult? = null
    }

    private object SyntheticWirelessLocatorRegistration {
        private var registered = false

        // NOT done eagerly at object-init - see the Refined Storage integration's
        // "API not loaded yet" lesson: register lazily on first real use instead, since AE2's
        // own init isn't guaranteed to have run yet this early.
        fun registerIfAbsent() {
            if (registered) return
            MenuLocators.register(
                SyntheticWirelessLocator::class.java,
                { _, _ -> },
                { _ ->
                    // The client decodes this too - MenuTypeBuilder.fromNetwork requires the
                    // resolved stack's item to actually be an IMenuItem (WirelessTerminalItem),
                    // or it refuses to construct the menu and disconnects the player. An empty
                    // placeholder stack fails that check; a real (uncharged, unlinked)
                    // WirelessTerminalItem stack satisfies it - the client never needs it to be
                    // charged or linked, only to be the right item type.
                    val item = AE2AccessImpl.wirelessTerminalItem()
                    SyntheticWirelessLocator(if (item != null) ItemStack(item) else ItemStack.EMPTY)
                }
            )
            MenuLocators.register(
                SyntheticWirelessCraftingLocator::class.java,
                { _, _ -> },
                { _ ->
                    val item = AE2AccessImpl.wirelessCraftingTerminalItem()
                    SyntheticWirelessCraftingLocator(if (item != null) ItemStack(item) else ItemStack.EMPTY)
                }
            )
            registered = true
        }
    }
}
