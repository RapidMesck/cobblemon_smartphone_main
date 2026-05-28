package com.nbp.cobblemon_smartphone.api

import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.isModLoaded
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object DatapackActionLoader : PreparableReloadListener {
    private val definitions = mutableListOf<DatapackActionDefinition>()
    private const val PATH_PREFIX = "smartphone_actions"
    private const val JSON_EXTENSION = ".json"

    fun getDefinitions(): List<DatapackActionDefinition> = definitions.toList()

    fun getActionCommands(actionId: String): List<String> =
        definitions.find { it.id == actionId }?.commands ?: emptyList()

    fun getActionCooldown(actionId: String): Int =
        definitions.find { it.id == actionId }?.cooldownSeconds ?: 0

    override fun reload(
        barrier: PreparableReloadListener.PreparationBarrier,
        resourceManager: ResourceManager,
        preparationProfiler: ProfilerFiller,
        reloadProfiler: ProfilerFiller,
        backgroundExecutor: Executor,
        gameExecutor: Executor
    ): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync({
            val loaded = mutableListOf<DatapackActionDefinition>()
            val resources = resourceManager.listResources(PATH_PREFIX) { id ->
                id.path.endsWith(JSON_EXTENSION)
            }

            for ((resourceId, _) in resources.entries) {
                try {
                    val json = resourceManager.getResourceOrThrow(resourceId)
                    json.open().use { stream ->
                        val reader = stream.bufferedReader()
                        val element = JsonParser.parseReader(reader)
                        val definition = DatapackActionDefinition.GSON.fromJson(
                            element, DatapackActionDefinition::class.java
                        )
                        if (definition != null && definition.id.isNotBlank() && definition.commands.isNotEmpty()) {
                            if (definition.requireMod != null && !isModLoaded(definition.requireMod)) {
                                CobblemonSmartphone.LOGGER.info(
                                    "Skipping datapack action '{}': required mod '{}' not loaded",
                                    definition.id, definition.requireMod
                                )
                                continue
                            }
                            loaded.add(definition)
                        }
                    }
                } catch (e: Exception) {
                    CobblemonSmartphone.LOGGER.error(
                        "Failed to load datapack action from {}", resourceId, e
                    )
                }
            }
            loaded.sortBy { it.order }
            loaded
        }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync({ loaded ->
            definitions.clear()
            definitions.addAll(loaded)
            CobblemonSmartphone.LOGGER.info("Loaded {} datapack actions", definitions.size)
        }, gameExecutor)
    }

    override fun getName() = "${CobblemonSmartphone.ID}:datapack_actions"
}
