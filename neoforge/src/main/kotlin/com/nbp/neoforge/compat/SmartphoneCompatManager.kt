package com.nbp.neoforge.compat

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.neoforge.compat.accessories.AccessoriesCompat
import com.nbp.neoforge.compat.curios.CuriosCompat
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList

/**
 * Manages optional mod compatibility for Cobblemon Smartphone on NeoForge.
 */
object SmartphoneCompatManager {

    private var curiosLoaded: Boolean = false
    private var accessoriesLoaded: Boolean = false

    /**
     * Initializes all optional mod compatibilities.
     * Should be called during mod initialization.
     */
    fun init() {
        curiosLoaded = ModList.get().isLoaded("curios")
        accessoriesLoaded = ModList.get().isLoaded("accessories")

        if (curiosLoaded) {
            try {
                CuriosCompat.register()
                CobblemonSmartphone.LOGGER.info("Curios compatibility initialized successfully")
            } catch (e: Exception) {
                CobblemonSmartphone.LOGGER.error("Failed to initialize Curios compatibility", e)
                curiosLoaded = false
            }
        }

        // Accessories is fully data-driven (slot + item tag), so no code registration is needed.
        if (accessoriesLoaded) {
            CobblemonSmartphone.LOGGER.info("Accessories compatibility initialized successfully")
        }
    }

    /**
     * Checks if Curios mod is loaded and available.
     */
    fun isCuriosLoaded(): Boolean = curiosLoaded

    /**
     * Checks if Accessories mod is loaded and available.
     */
    fun isAccessoriesLoaded(): Boolean = accessoriesLoaded

    /**
     * Gets a smartphone from Curios slots if available.
     * @param player The player to check
     * @return The smartphone ItemStack if found in Curios slots, null otherwise
     */
    fun getSmartphoneFromCurios(player: Player): ItemStack? {
        if (!curiosLoaded) return null
        return try {
            CuriosCompat.getEquippedSmartphone(player)
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error("Error checking Curios slots", e)
            null
        }
    }

    /**
     * Gets a smartphone from Accessories slots if available.
     * @param player The player to check
     * @return The smartphone ItemStack if found in Accessories slots, null otherwise
     */
    fun getSmartphoneFromAccessories(player: Player): ItemStack? {
        if (!accessoriesLoaded) return null
        return try {
            AccessoriesCompat.getEquippedSmartphone(player)
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error("Error checking Accessories slots", e)
            null
        }
    }

    /**
     * Gets a smartphone from the player's inventory or equipment slots.
     * Curios and Accessories slots are checked first if available.
     * @param player The player to check
     * @return The smartphone ItemStack if found, null otherwise
     */
    fun getSmartphone(player: Player): ItemStack? {
        // First check Curios slots if available
        if (curiosLoaded) {
            val curiosSmartphone = getSmartphoneFromCurios(player)
            if (curiosSmartphone != null) {
                return curiosSmartphone
            }
        }

        // Then check Accessories slots if available
        if (accessoriesLoaded) {
            val accessoriesSmartphone = getSmartphoneFromAccessories(player)
            if (accessoriesSmartphone != null) {
                return accessoriesSmartphone
            }
        }

        // Fallback to regular inventory check
        return player.inventory.items.firstOrNull { it.item is SmartphoneItem }
    }

    /**
     * Gets the SmartphoneItem from the found smartphone, if any.
     * @param player The player to check
     * @return The SmartphoneItem if a smartphone was found, null otherwise
     */
    fun getSmartphoneItem(player: Player): SmartphoneItem? {
        return getSmartphone(player)?.item as? SmartphoneItem
    }
}
