package com.nbp.cobblemon_smartphone.registry

import com.nbp.cobblemon_smartphone.util.smartphoneResource
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

abstract class RegistryProvider<R : Registry<T>, K : ResourceKey<R>, T> {
    abstract val registry: R

    abstract val resourceKey: K

    private val queue = hashMapOf<ResourceLocation, T>()

    open fun <E : T> add(name: String, entry: E): E {
        val id = smartphoneResource(name)
        queue[id] = entry
        return entry
    }

    open fun register(consumer: (ResourceLocation, T) -> Unit) {
        queue.forEach(consumer)
    }

    open fun all(): Collection<T> = queue.values.toList()
}