package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.item.SmartphoneItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingTransformRecipe.class)
public class MixinSmithingTransformRecipe {

    private static final String UPGRADES_TAG = "cobblemon_smartphone:upgrades";
    private static final String EXTRA_CAPACITY_TAG = "cobblemon_smartphone:extra_energy_capacity";

    @Shadow
    private ItemStack result;

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    public void onAssemble(SmithingRecipeInput input, HolderLookup.Provider registries,
                           CallbackInfoReturnable<ItemStack> cir) {
        ItemStack base = input.base();

        // Only intercept if the base is a smartphone
        if (!(base.getItem() instanceof SmartphoneItem)) {
            return;
        }

        CompoundTag recipeData = getResultCustomData();
        if (recipeData == null) {
            return;
        }

        boolean hasUpgrades = recipeData.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND);
        boolean hasCapacity = recipeData.contains(EXTRA_CAPACITY_TAG, CompoundTag.TAG_INT);
        if (!hasUpgrades && !hasCapacity) {
            return;
        }

        // Copy the base smartphone (preserves color) and add upgrades/capacity
        ItemStack output = base.copy();
        output.setCount(1);
        applyRecipeData(output, recipeData);

        cir.setReturnValue(output);
    }

    private CompoundTag getResultCustomData() {
        if (this.result == null || this.result.isEmpty()) return null;
        CustomData customData = this.result.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        return customData.copyTag();
    }

    private void applyRecipeData(ItemStack stack, CompoundTag recipeData) {
        CustomData existingData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existingData != null ? existingData.copyTag() : new CompoundTag();

        if (recipeData.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND)) {
            CompoundTag recipeUpgrades = recipeData.getCompound(UPGRADES_TAG);
            CompoundTag targetUpgrades = tag.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND)
                    ? tag.getCompound(UPGRADES_TAG) : new CompoundTag();
            for (String key : recipeUpgrades.getAllKeys()) {
                if (recipeUpgrades.getBoolean(key)) {
                    targetUpgrades.putBoolean(key, true);
                }
            }
            tag.put(UPGRADES_TAG, targetUpgrades);
        }

        if (recipeData.contains(EXTRA_CAPACITY_TAG, CompoundTag.TAG_INT)) {
            int extra = recipeData.getInt(EXTRA_CAPACITY_TAG);
            int current = tag.getInt(EXTRA_CAPACITY_TAG);
            tag.putInt(EXTRA_CAPACITY_TAG, current + extra);
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
