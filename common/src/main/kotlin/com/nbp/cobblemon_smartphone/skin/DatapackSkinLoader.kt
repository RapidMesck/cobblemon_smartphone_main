package com.nbp.cobblemon_smartphone.skin

import com.google.gson.JsonParser
import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object DatapackSkinLoader : PreparableReloadListener {
    private const val DIR = "smartphone_skins"
    private val definitions = mutableListOf<SmartphoneSkin>()

    fun getSkins(): List<SmartphoneSkin> = synchronized(definitions) { definitions.toList() }

    override fun reload(
        barrier: PreparableReloadListener.PreparationBarrier,
        resourceManager: ResourceManager,
        preparationProfiler: ProfilerFiller,
        reloadProfiler: ProfilerFiller,
        backgroundExecutor: Executor,
        gameExecutor: Executor
    ): CompletableFuture<Void> {
        return CompletableFuture.supplyAsync({
            val loaded = mutableListOf<SmartphoneSkin>()
            val resources = resourceManager.listResources(DIR) { it.path.endsWith(".json") }
            for ((location, resource) in resources) {
                try {
                    resource.open().use { stream ->
                        val obj = JsonParser.parseReader(stream.bufferedReader()).asJsonObject
                        val skin = SmartphoneSkinRegistry.parseSkinJson(location, obj, null)
                        if (skin != null && skin.id != SmartphoneSkin.DEFAULT_ID) {
                            loaded.add(skin)
                        }
                    }
                } catch (e: Exception) {
                    CobblemonSmartphone.LOGGER.warn("Failed to parse datapack smartphone skin '{}': {}", location, e.message)
                }
            }
            loaded
        }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync({ loaded ->
            synchronized(definitions) {
                definitions.clear()
                definitions.addAll(loaded)
            }
            SmartphoneSkinRegistry.updateDatapackSkins(loaded)
        }, gameExecutor)
    }
}
