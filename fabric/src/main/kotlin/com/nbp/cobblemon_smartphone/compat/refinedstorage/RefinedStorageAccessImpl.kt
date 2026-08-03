package com.nbp.cobblemon_smartphone.compat.refinedstorage

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi
import com.refinedmods.refinedstorage.common.api.support.network.item.NetworkItemContext
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReference
import com.refinedmods.refinedstorage.common.api.support.slotreference.SlotReferenceFactory
import com.refinedmods.refinedstorage.common.grid.WirelessGridItem
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.Optional

/**
 * `WirelessGridItem`'s grid-opening logic (`use(name, player, slotReference, context)`) is
 * `protected`, with no public entry point that takes an already-built context - the public
 * overloads all assume a real held item. Constructing a throwaway `WirelessGridItem` instance
 * to call it as a subclass doesn't work: in this Minecraft version, `Item`'s constructor
 * always tries to register an "intrusive holder" and crashes ("This registry can't create
 * intrusive holders") once the item registry is frozen, i.e. any time after the game finished
 * loading. So instead this reflects onto the *real*, already-registered `refinedstorage:
 * wireless_grid` item singleton to call its inherited protected method directly - not a
 * mixin, no bytecode transform, just standard `setAccessible` reflection. `WirelessGridItem`
 * isn't part of Refined Storage's `-api` module and carries no stability guarantee; a future
 * RS2 update could rename/remove this method, requiring this bridge to be revisited.
 *
 * The grid's own "active" state additionally depends on the *item* carrying real RS2 energy
 * (RS2's own network-energy check is separate and unaffected by any of this) - a real Wireless
 * Grid item only shows as connected when charged. Our smartphone isn't an RS2 energy item at
 * all, so the [SlotReference] passed in resolves to a synthetic, always-freshly-charged
 * `wireless_grid` `ItemStack` built via the public, stable `EnergyItemHelper.createAtEnergyCapacity`
 * - never placed in the player's real inventory, so nothing is visible, and rebuilt on every
 * call so repeated opens can't run it down.
 *
 * That [SlotReference] gets serialized to the client as part of the menu-open packet (RS2
 * syncs which inventory slot to grey out while the grid is open), so it needs a genuinely
 * registered [SlotReferenceFactory] - a naive `getFactory()` that throws crashes the encode
 * and disconnects the player. [SyntheticSlotReferenceFactory] is registered with RS2's own
 * `PlatformRegistry` extension point and uses `StreamCodec.unit(...)` (writes nothing, always
 * decodes to the same inert placeholder) since the client never needs to actually resolve this
 * reference to a real stack.
 */
object RefinedStorageAccessImpl : RefinedStorageAccess {
    // NOT done eagerly at object-init: this compat object is touched during
    // SmartphoneCompatManager's own mod-init phase, which on Fabric can run before Refined
    // Storage's *own* init has populated its RefinedStorageApiProxy delegate ("API not loaded
    // yet" crash on startup). Registered lazily on first real use instead - by the time a
    // player actually triggers the action, every mod has long finished loading.
    private val useMethod by lazy {
        WirelessGridItem::class.java.getDeclaredMethod(
            "use",
            Component::class.java,
            ServerPlayer::class.java,
            SlotReference::class.java,
            NetworkItemContext::class.java
        ).apply { isAccessible = true }
    }

    private fun wirelessGridItem(): WirelessGridItem? {
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", "wireless_grid"))
        return item as? WirelessGridItem
    }

    override fun isBound(stack: ItemStack): Boolean =
        RefinedStorageApi.INSTANCE.networkItemHelper.isBound(stack)

    override fun isReachable(player: ServerPlayer, stack: ItemStack): Boolean =
        createContext(player, stack)?.resolveNetwork()?.isPresent ?: false

    override fun openGrid(player: ServerPlayer, stack: ItemStack) {
        val item = wirelessGridItem()
        if (item == null) {
            CobblemonSmartphone.LOGGER.error("refinedstorage:wireless_grid item not found in registry")
            return
        }
        val slotReference = chargedSlotReference(item)
        val context = RefinedStorageApi.INSTANCE.networkItemHelper.createContext(stack, player, slotReference)
        useMethod.invoke(item, null, player, slotReference, context)
    }

    private fun createContext(player: ServerPlayer, stack: ItemStack): NetworkItemContext? {
        val item = wirelessGridItem() ?: return null
        return RefinedStorageApi.INSTANCE.networkItemHelper.createContext(stack, player, chargedSlotReference(item))
    }

    private fun chargedSlotReference(item: WirelessGridItem): SlotReference {
        SyntheticSlotReferenceFactory.registerIfAbsent()
        val chargedStack = RefinedStorageApi.INSTANCE.energyItemHelper.createAtEnergyCapacity(item)
        return ChargedSlotReference(chargedStack)
    }

    /** Server-side only - resolves to the freshly-charged in-memory stack. Never sent as-is. */
    private class ChargedSlotReference(private val stack: ItemStack) : SlotReference {
        override fun isDisabledSlot(playerSlotIndex: Int): Boolean = false
        override fun resolve(player: Player): Optional<ItemStack> = Optional.of(stack)
        override fun getFactory(): SlotReferenceFactory = SyntheticSlotReferenceFactory
    }

    /** What a client decodes into - inert, since the client never needs to resolve this. */
    private object InertClientSlotReference : SlotReference {
        override fun isDisabledSlot(playerSlotIndex: Int): Boolean = false
        override fun resolve(player: Player): Optional<ItemStack> = Optional.empty()
        override fun getFactory(): SlotReferenceFactory = SyntheticSlotReferenceFactory
    }

    private object SyntheticSlotReferenceFactory : SlotReferenceFactory {
        private val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CobblemonSmartphone.ID,
            "refinedstorage_synthetic_grid"
        )
        // Not StreamCodec.unit(...): that encodes nothing but still requires the encoded
        // value be reference-equal to the fixed instance, and throws otherwise - our real
        // (server-side) ChargedSlotReference instances are never that instance. This ignores
        // whatever's passed to encode entirely, writing zero bytes either way.
        private val codec: StreamCodec<RegistryFriendlyByteBuf, SlotReference> = StreamCodec.of(
            { _, _ -> },
            { _ -> InertClientSlotReference }
        )
        private var registered = false

        fun registerIfAbsent() {
            if (registered) return
            RefinedStorageApi.INSTANCE.slotReferenceFactoryRegistry.register(ID, this)
            registered = true
        }

        override fun getStreamCodec(): StreamCodec<RegistryFriendlyByteBuf, SlotReference> = codec
    }
}
