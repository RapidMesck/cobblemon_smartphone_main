package com.nbp.cobblemon_smartphone.skin

import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.theme.SmartphoneTheme
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.upgrade.hasUpgrade
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

fun ItemStack.getSkinId(): String? {
    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return null
    val tag = customData.copyTag()
    val namespaced = tag.getString(SmartphoneSkinHelper.SKIN_TAG).takeIf { it.isNotBlank() }
    if (namespaced != null) return namespaced
    val short = tag.getString(SmartphoneSkinHelper.SHORT_SKIN_TAG).takeIf { it.isNotBlank() }
    if (short != null) return short
    return null
}

fun ItemStack.setSkinId(skinId: String?) {
    val customData = this.get(DataComponents.CUSTOM_DATA)
    val tag = customData?.copyTag() ?: CompoundTag()
    if (skinId.isNullOrBlank() || skinId == SmartphoneSkin.DEFAULT_ID) {
        tag.remove(SmartphoneSkinHelper.SKIN_TAG)
        tag.remove(SmartphoneSkinHelper.SHORT_SKIN_TAG)
    } else {
        tag.putString(SmartphoneSkinHelper.SKIN_TAG, skinId)
    }
    if (tag.isEmpty) {
        this.remove(DataComponents.CUSTOM_DATA)
    } else {
        this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }
}

fun ItemStack.isSkinUnlocked(skin: SmartphoneSkin): Boolean {
    if (skin.id == SmartphoneSkin.DEFAULT_ID) return true
    if (!skin.locked && skin.requiredUpgrade == null) return true
    if (skin.requiredUpgrade != null && this.hasUpgrade(skin.requiredUpgrade)) return true
    if (this.getSkinId() == skin.id) return true

    val customData = this.get(DataComponents.CUSTOM_DATA) ?: return false
    val tag = customData.copyTag()
    if (!tag.contains(SmartphoneSkinHelper.UNLOCKED_SKINS_TAG, CompoundTag.TAG_COMPOUND.toInt())) return false
    return tag.getCompound(SmartphoneSkinHelper.UNLOCKED_SKINS_TAG).getBoolean(skin.id)
}

fun ItemStack.unlockSkin(skinId: String) {
    if (skinId.isBlank() || skinId == SmartphoneSkin.DEFAULT_ID) return
    val customData = this.get(DataComponents.CUSTOM_DATA)
    val tag = customData?.copyTag() ?: CompoundTag()
    val unlocked = if (tag.contains(SmartphoneSkinHelper.UNLOCKED_SKINS_TAG, CompoundTag.TAG_COMPOUND.toInt())) {
        tag.getCompound(SmartphoneSkinHelper.UNLOCKED_SKINS_TAG)
    } else {
        CompoundTag()
    }
    unlocked.putBoolean(skinId, true)
    tag.put(SmartphoneSkinHelper.UNLOCKED_SKINS_TAG, unlocked)
    this.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
}

object SmartphoneSkinHelper {
    const val SKIN_TAG = "cobblemon_smartphone:skin"
    const val SHORT_SKIN_TAG = "smartphone_skin"
    const val UNLOCKED_SKINS_TAG = "cobblemon_smartphone:unlocked_skins"

    fun resolveSkin(stack: ItemStack?): SmartphoneSkin {
        val effectiveStack = stack
            ?: SmartphoneHelper.contextSmartphone
            ?: SmartphoneThemeHelper.clientPlayerProvider?.invoke()?.let { SmartphoneHelper.getSmartphone(it) }
        val skinId = effectiveStack?.getSkinId() ?: return SmartphoneSkin.DEFAULT
        return SmartphoneSkinRegistry.getSkin(skinId)
    }

    @JvmStatic
    fun resolveHandModel(stack: ItemStack, color: SmartphoneColor, isRotom: Boolean): ResourceLocation {
        val skin = resolveSkin(stack)
        if (skin.supportsColor(color)) {
            val override = if (isRotom) skin.rotomModel else skin.handModel
            if (override != null) return override
        }
        return if (isRotom) color.getRotomModelPath() else color.getHandModelPath()
    }

    @JvmStatic
    fun resolveInventoryModel(stack: ItemStack, color: SmartphoneColor): ResourceLocation? {
        val skin = resolveSkin(stack)
        if (!skin.supportsColor(color)) return null
        return skin.inventoryModel
    }

    fun resolveFrameTexture(stack: ItemStack?, theme: SmartphoneTheme, color: SmartphoneColor): ResourceLocation {
        val skin = resolveSkin(stack)
        if (skin.supportsColor(color) && skin.frameTexture != null) {
            return skin.frameTexture
        }
        return theme.getFrameTexture(color.getSmallScreenTexture())
    }

    fun resolveLargeFrameTexture(stack: ItemStack?, theme: SmartphoneTheme, color: SmartphoneColor): ResourceLocation {
        val skin = resolveSkin(stack)
        if (skin.supportsColor(color) && skin.largeFrameTexture != null) {
            return skin.largeFrameTexture
        }
        return theme.getLargeFrameTexture(color.getLargeScreenTexture())
    }
}
