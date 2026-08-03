package com.nbp.cobblemon_smartphone.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item

/**
 * Non-obtainable items that exist purely to give an overlay a 3D model to render (see
 * [com.nbp.cobblemon_smartphone.client.gui.GpsCompassOverlay]). Kept out of
 * [CobblemonSmartphoneItems] because Curios/Trinkets compat iterate that registry's `all()` and
 * register every entry as a wearable smartphone; these items are never obtainable or added to a
 * creative tab, so they're not registered there.
 */
object CobblemonSmartphoneGpsItems : RegistryProvider<Registry<Item>, ResourceKey<Registry<Item>>, Item>() {
    override val registry: Registry<Item> = BuiltInRegistries.ITEM
    override val resourceKey: ResourceKey<Registry<Item>> = Registries.ITEM

    val GPS_ARROW: Item = add("gps_arrow", Item(Item.Properties()))
}
