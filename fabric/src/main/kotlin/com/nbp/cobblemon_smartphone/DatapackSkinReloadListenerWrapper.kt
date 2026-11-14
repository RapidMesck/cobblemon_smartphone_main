package com.nbp.cobblemon_smartphone

import com.nbp.cobblemon_smartphone.skin.DatapackSkinLoader
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class DatapackSkinReloadListenerWrapper : IdentifiableResourceReloadListener {
    override fun getFabricId() = ResourceLocation.fromNamespaceAndPath(
        CobblemonSmartphone.ID, "datapack_skins"
    )

    override fun reload(
        barrier: PreparableReloadListener.PreparationBarrier,
        resourceManager: ResourceManager,
        preparationProfiler: ProfilerFiller,
        reloadProfiler: ProfilerFiller,
        backgroundExecutor: Executor,
        gameExecutor: Executor
    ): CompletableFuture<Void> {
        return DatapackSkinLoader.reload(
            barrier, resourceManager,
            preparationProfiler, reloadProfiler,
            backgroundExecutor, gameExecutor
        )
    }

    override fun getName() = DatapackSkinLoader.getName()
}
