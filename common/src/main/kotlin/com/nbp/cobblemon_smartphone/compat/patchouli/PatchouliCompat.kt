package com.nbp.cobblemon_smartphone.compat.patchouli

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

object PatchouliCompat {
    const val MOD_ID = "patchouli"

    fun isAvailable(): Boolean = isModLoaded(MOD_ID)

    fun openBook(player: ServerPlayer, bookId: ResourceLocation): Boolean {
        if (!isAvailable()) return false
        return try {
            val apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI")
            val getMethod = apiClass.getMethod("get")
            val apiInstance = getMethod.invoke(null) ?: return false

            try {
                val isStubMethod = apiInstance.javaClass.getMethod("isStub")
                if (isStubMethod.invoke(apiInstance) as? Boolean == true) {
                    CobblemonSmartphone.LOGGER.warn("Patchouli API is a stub; cannot open book {}", bookId)
                    return false
                }
            } catch (_: NoSuchMethodException) {
                // Ignore if method not present
            }

            val openMethod = apiInstance.javaClass.getMethod(
                "openBookGUI",
                ServerPlayer::class.java,
                ResourceLocation::class.java
            )
            openMethod.invoke(apiInstance, player, bookId)
            true
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error(
                "Failed to open Patchouli book '{}' for player {}",
                bookId,
                player.name.string,
                e
            )
            false
        }
    }

    fun openBookClient(bookId: ResourceLocation): Boolean {
        if (!isAvailable()) return false
        return try {
            val apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI")
            val getMethod = apiClass.getMethod("get")
            val apiInstance = getMethod.invoke(null) ?: return false

            try {
                val isStubMethod = apiInstance.javaClass.getMethod("isStub")
                if (isStubMethod.invoke(apiInstance) as? Boolean == true) {
                    return false
                }
            } catch (_: NoSuchMethodException) {
                // Ignore if method not present
            }

            val openMethod = apiInstance.javaClass.getMethod(
                "openBookGUI",
                ResourceLocation::class.java
            )
            openMethod.invoke(apiInstance, bookId)
            true
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.error("Failed to open Patchouli book '{}' on client", bookId, e)
            false
        }
    }
}
