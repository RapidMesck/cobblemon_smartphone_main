package com.nbp.cobblemon_smartphone.skin

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.ConcurrentHashMap

object SmartphoneSkinRegistry {
    private const val DIR = "smartphone_skins"

    @Volatile
    var clientResourceManagerProvider: (() -> ResourceManager?)? = null

    private val resourcePackSkins = LinkedHashMap<String, SmartphoneSkin>()
    private val datapackSkins = ConcurrentHashMap<String, SmartphoneSkin>()
    private var lastResourceManager: ResourceManager? = null

    fun getSkins(color: SmartphoneColor? = null): List<SmartphoneSkin> {
        ensureLoadedFromClient()
        val merged = LinkedHashMap<String, SmartphoneSkin>()
        merged[SmartphoneSkin.DEFAULT_ID] = SmartphoneSkin.DEFAULT
        synchronized(resourcePackSkins) {
            merged.putAll(resourcePackSkins)
        }
        merged.putAll(datapackSkins)
        val all = merged.values.toList()
        return if (color == null) all else all.filter { it.supportsColor(color) }
    }

    fun getDatapackSkin(id: String?): SmartphoneSkin? {
        if (id.isNullOrBlank()) return null
        return datapackSkins[id]
    }

    fun getSkin(id: String?): SmartphoneSkin {
        if (id.isNullOrBlank() || id == SmartphoneSkin.DEFAULT_ID) return SmartphoneSkin.DEFAULT
        ensureLoadedFromClient()
        datapackSkins[id]?.let { return it }
        synchronized(resourcePackSkins) {
            resourcePackSkins[id]?.let { return it }
        }
        return SmartphoneSkin.DEFAULT
    }

    fun getAllModelLocations(): Set<ResourceLocation> {
        val locs = LinkedHashSet<ResourceLocation>()
        synchronized(resourcePackSkins) {
            for (skin in resourcePackSkins.values) {
                skin.handModel?.let(locs::add)
                skin.rotomModel?.let(locs::add)
                skin.inventoryModel?.let(locs::add)
            }
        }
        for (skin in datapackSkins.values) {
            skin.handModel?.let(locs::add)
            skin.rotomModel?.let(locs::add)
            skin.inventoryModel?.let(locs::add)
        }
        return locs
    }

    fun updateDatapackSkins(skins: List<SmartphoneSkin>) {
        datapackSkins.clear()
        for (skin in skins) {
            if (skin.id != SmartphoneSkin.DEFAULT_ID) {
                datapackSkins[skin.id] = skin
            }
        }
    }

    fun clearDatapackSkins() {
        datapackSkins.clear()
    }

    private fun ensureLoadedFromClient() {
        val rm = try {
            clientResourceManagerProvider?.invoke()
        } catch (_: Throwable) {
            null
        }
        if (rm != null && lastResourceManager !== rm) {
            reload(rm)
        }
    }

    fun ensureLoaded(resourceManager: ResourceManager) {
        if (lastResourceManager !== resourceManager) {
            reload(resourceManager)
        }
    }

    @JvmStatic
    fun reload(resourceManager: ResourceManager) {
        lastResourceManager = resourceManager
        val loaded = LinkedHashMap<String, SmartphoneSkin>()
        val resources = try {
            resourceManager.listResources(DIR) { it.path.endsWith(".json") }
        } catch (e: Exception) {
            CobblemonSmartphone.LOGGER.warn("Failed to list smartphone_skins from resource packs: {}", e.message)
            emptyMap()
        }

        for ((location, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val obj = JsonParser.parseReader(stream.bufferedReader()).asJsonObject
                    val skin = parseSkinJson(location, obj, resourceManager)
                    if (skin != null && skin.id != SmartphoneSkin.DEFAULT_ID) {
                        loaded[skin.id] = skin
                    }
                }
            } catch (e: Exception) {
                CobblemonSmartphone.LOGGER.warn("Failed to parse smartphone skin '{}': {}", location, e.message)
            }
        }

        synchronized(resourcePackSkins) {
            resourcePackSkins.clear()
            resourcePackSkins.putAll(loaded)
        }
    }

    fun parseSkinJson(
        location: ResourceLocation,
        obj: JsonObject,
        resourceManager: ResourceManager? = null
    ): SmartphoneSkin? {
        val rawId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        val id = if (!rawId.isNullOrBlank()) {
            rawId.trim()
        } else {
            val pathWithoutExt = location.path.removePrefix("$DIR/").removeSuffix(".json")
            "${location.namespace}:$pathWithoutExt"
        }

        val nameStr = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "skin.$id"
        val name = if (nameStr.contains('.')) Component.translatable(nameStr) else Component.literal(nameStr)

        val handModel = parseValidatedModel(obj, "hand_model", id, resourceManager)
        val rotomModel = parseValidatedModel(obj, "rotom_model", id, resourceManager)
        val inventoryModel = parseValidatedModel(obj, "inventory_model", id, resourceManager)
        val frameTexture = parseValidatedTexture(obj, "frame", id, resourceManager)
        val largeFrameTexture = parseValidatedTexture(obj, "large_frame", id, resourceManager)

        if (handModel == null && rotomModel == null && inventoryModel == null && frameTexture == null && largeFrameTexture == null) {
            return null
        }

        val colors = LinkedHashSet<String>()
        val colorsElem = obj.get("colors")
        if (colorsElem != null && colorsElem.isJsonArray) {
            for (el in colorsElem.asJsonArray) {
                if (el.isJsonPrimitive) {
                    val c = el.asString.trim().lowercase()
                    if (c.isNotEmpty()) colors.add(c)
                }
            }
        }

        val locked = (obj.get("locked")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) ||
            (obj.get("require_unlock")?.takeIf { it.isJsonPrimitive }?.asBoolean == true)
        val requiredUpgrade = (obj.get("require_upgrade") ?: obj.get("required_upgrade"))
            ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }

        return SmartphoneSkin(
            id = id,
            name = name,
            handModel = handModel,
            rotomModel = rotomModel,
            inventoryModel = inventoryModel,
            frameTexture = frameTexture,
            largeFrameTexture = largeFrameTexture,
            allowedColors = colors,
            locked = locked,
            requiredUpgrade = requiredUpgrade
        )
    }

    private fun parseValidatedTexture(
        obj: JsonObject,
        field: String,
        skinId: String,
        resourceManager: ResourceManager?
    ): ResourceLocation? {
        val raw = obj.get(field)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
        if (raw.isEmpty()) return null
        val loc = ResourceLocation.tryParse(raw)
        if (loc == null) {
            CobblemonSmartphone.LOGGER.warn("Skin '{}' has invalid {} '{}'", skinId, field, raw)
            return null
        }
        if (resourceManager != null) {
            val exists = try {
                resourceManager.getResource(loc).isPresent
            } catch (_: Exception) {
                false
            }
            if (!exists) {
                CobblemonSmartphone.LOGGER.warn("Skin '{}' specifies missing texture '{}'", skinId, loc)
                return null
            }
        }
        return loc
    }

    private fun parseValidatedModel(
        obj: JsonObject,
        field: String,
        skinId: String,
        resourceManager: ResourceManager?
    ): ResourceLocation? {
        val raw = obj.get(field)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
        if (raw.isEmpty()) return null
        val loc = ResourceLocation.tryParse(raw)
        if (loc == null) {
            CobblemonSmartphone.LOGGER.warn("Skin '{}' has invalid {} '{}'", skinId, field, raw)
            return null
        }
        if (resourceManager != null) {
            val modelJson = ResourceLocation.fromNamespaceAndPath(loc.namespace, "models/${loc.path}.json")
            val exists = try {
                resourceManager.getResource(modelJson).isPresent
            } catch (_: Exception) {
                false
            }
            if (!exists) {
                CobblemonSmartphone.LOGGER.warn(
                    "Skin '{}' specifies missing model '{}' (expected {})",
                    skinId, loc, modelJson
                )
            }
        }
        return loc
    }
}
