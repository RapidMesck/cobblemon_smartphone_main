package com.nbp.cobblemon_smartphone.ui.datapack

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Loads custom smartphone HD screen schemas from `data/<namespace>/smartphone_screens/<path>.json`.
 * Keeps parsed [DatapackScreenDefinition]s and raw JSON strings for fast client synchronization.
 */
object DatapackScreenLoader : PreparableReloadListener {
    private const val PATH_PREFIX = "smartphone_screens"
    private const val JSON_EXTENSION = ".json"

    private data class LoadedScreen(
        val definition: DatapackScreenDefinition,
        val rawJson: String
    )

    private val serverScreens = ConcurrentHashMap<String, LoadedScreen>()

    /** Callback triggered on game executor after `/reload` finishes so active servers can re-sync players. */
    var onReloadComplete: (() -> Unit)? = null

    fun getScreen(id: String): DatapackScreenDefinition? = serverScreens[normalizeId(id)]?.definition

    fun getAllScreens(): List<DatapackScreenDefinition> = serverScreens.values.map { it.definition }

    fun getSyncPayloads(): List<Pair<String, String>> =
        serverScreens.entries.map { it.key to it.value.rawJson }

    fun normalizeId(id: String): String = id.trim().lowercase()

    /**
     * Parses a JSON string and fills [DatapackScreenDefinition.id] from [fallbackId] if omitted.
     */
    fun parseScreen(rawJson: String, fallbackId: String? = null): DatapackScreenDefinition? {
        val parsed = DatapackScreenDefinition.fromJson(rawJson) ?: return null
        val resolvedId = parsed.id.ifBlank { fallbackId.orEmpty() }.trim()
        if (resolvedId.isBlank()) return null
        return if (parsed.id == resolvedId) parsed else parsed.copy(id = resolvedId)
    }

    internal fun deriveIdFromResource(resourceId: ResourceLocation): String {
        val trimmedPath = resourceId.path
            .removePrefix("$PATH_PREFIX/")
            .removeSuffix(JSON_EXTENSION)
        return "${resourceId.namespace}:$trimmedPath"
    }

    override fun reload(
        barrier: PreparableReloadListener.PreparationBarrier,
        resourceManager: ResourceManager,
        preparationProfiler: ProfilerFiller,
        reloadProfiler: ProfilerFiller,
        backgroundExecutor: Executor,
        gameExecutor: Executor
    ): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync({
            val loaded = LinkedHashMap<String, LoadedScreen>()
            val resources = resourceManager.listResources(PATH_PREFIX) { id ->
                id.path.endsWith(JSON_EXTENSION)
            }
            for ((resourceId, _) in resources.entries.sortedBy { it.key.toString() }) {
                try {
                    val rawJson = resourceManager.getResourceOrThrow(resourceId).open().use { stream ->
                        stream.bufferedReader().readText()
                    }
                    val fallbackId = deriveIdFromResource(resourceId)
                    val def = parseScreen(rawJson, fallbackId)
                    if (def == null) {
                        CobblemonSmartphone.LOGGER.warn("Skipping invalid datapack screen JSON: {}", resourceId)
                        continue
                    }
                    if (!def.requireMod.isNullOrBlank() && !isModLoaded(def.requireMod)) {
                        CobblemonSmartphone.LOGGER.info(
                            "Skipping datapack screen '{}': required mod '{}' not loaded",
                            def.id,
                            def.requireMod
                        )
                        continue
                    }
                    loaded[normalizeId(def.id)] = LoadedScreen(def, rawJson)
                } catch (e: Exception) {
                    CobblemonSmartphone.LOGGER.error("Failed to load datapack screen from {}", resourceId, e)
                }
            }
            loaded
        }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync({ loaded ->
            serverScreens.clear()
            serverScreens.putAll(loaded)
            CobblemonSmartphone.LOGGER.info("Loaded {} datapack smartphone screens", serverScreens.size)
            onReloadComplete?.invoke()
        }, gameExecutor)
    }

    override fun getName(): String = "${CobblemonSmartphone.ID}:datapack_screens"
}

/**
 * Client-side registry holding synced [DatapackScreenDefinition]s and live server placeholder/list snapshots.
 */
object DatapackScreenClientState {
    data class ScreenDataSnapshot(
        val placeholders: Map<String, String> = emptyMap(),
        val lists: Map<String, List<Map<String, String>>> = emptyMap(),
        val updatedAtMs: Long = System.currentTimeMillis()
    )

    private val clientScreens = ConcurrentHashMap<String, DatapackScreenDefinition>()
    private val dataSnapshots = ConcurrentHashMap<String, ScreenDataSnapshot>()

    fun acceptScreens(entries: List<Pair<String, String>>) {
        clientScreens.clear()
        for ((id, rawJson) in entries) {
            val def = DatapackScreenLoader.parseScreen(rawJson, id) ?: continue
            clientScreens[DatapackScreenLoader.normalizeId(def.id)] = def
        }
    }

    /** Registers a local definition directly (used for offline testing or screenshot harness demos). */
    fun registerLocal(definition: DatapackScreenDefinition) {
        val key = DatapackScreenLoader.normalizeId(definition.id)
        if (key.isNotEmpty()) {
            clientScreens[key] = definition
        }
    }

    fun getScreen(id: String): DatapackScreenDefinition? =
        clientScreens[DatapackScreenLoader.normalizeId(id)] ?: DatapackScreenLoader.getScreen(id)

    fun getAllScreens(): List<DatapackScreenDefinition> = clientScreens.values.toList()

    fun acceptData(
        screenId: String,
        placeholders: Map<String, String>,
        lists: Map<String, List<Map<String, String>>>
    ) {
        dataSnapshots[DatapackScreenLoader.normalizeId(screenId)] = ScreenDataSnapshot(placeholders, lists)
    }

    fun getData(screenId: String): ScreenDataSnapshot? =
        dataSnapshots[DatapackScreenLoader.normalizeId(screenId)]

    fun clearData(screenId: String? = null) {
        if (screenId == null) dataSnapshots.clear()
        else dataSnapshots.remove(DatapackScreenLoader.normalizeId(screenId))
    }
}
