package com.nbp.cobblemon_smartphone.compat.tomsstorage

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level

private const val LINKS_TAG = "cobblemon_smartphone:links"
private const val LINK_KEY = "toms_storage"

/**
 * Stored on the smartphone's own custom_data, independent of Toms Storage's own item
 * components, so linking works without the mod on the compile/runtime classpath.
 */
fun ItemStack.getTomsStorageLink(): Pair<ResourceKey<Level>, BlockPos>? {
    val tag = this.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
    if (!tag.contains(LINKS_TAG, CompoundTag.TAG_COMPOUND.toInt())) return null
    val links = tag.getCompound(LINKS_TAG)
    if (!links.contains(LINK_KEY, CompoundTag.TAG_COMPOUND.toInt())) return null
    val link = links.getCompound(LINK_KEY)
    val dimension = ResourceLocation.tryParse(link.getString("dimension")) ?: return null
    val pos = BlockPos(link.getInt("x"), link.getInt("y"), link.getInt("z"))
    return ResourceKey.create(Registries.DIMENSION, dimension) to pos
}

fun ItemStack.setTomsStorageLink(dimension: ResourceKey<Level>, pos: BlockPos) {
    val tag = this.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()
    val links = if (tag.contains(LINKS_TAG, CompoundTag.TAG_COMPOUND.toInt())) {
        tag.getCompound(LINKS_TAG)
    } else {
        CompoundTag()
    }
    val link = CompoundTag()
    link.putString("dimension", dimension.location().toString())
    link.putInt("x", pos.x)
    link.putInt("y", pos.y)
    link.putInt("z", pos.z)
    links.put(LINK_KEY, link)
    tag.put(LINKS_TAG, links)
    this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
}
