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

        // Read upgrade keys from the recipe's result NBT (generic — works for any upgrade)
        CompoundTag upgrades = getUpgradesFromResult();
        if (upgrades == null || upgrades.isEmpty()) {
            return;
        }

        // Copy the base smartphone (preserves color) and add all upgrade keys
        ItemStack output = base.copy();
        output.setCount(1);
        applyUpgrades(output, upgrades);

        cir.setReturnValue(output);
    }

    /**
     * Extracts the upgrade compound from the recipe's fixed result ItemStack.
     * Reads {@code minecraft:custom_data -> cobblemon_smartphone:upgrades}.
     * @return the upgrades compound tag, or null if not present
     */
    private CompoundTag getUpgradesFromResult() {
        if (this.result == null || this.result.isEmpty()) return null;
        CustomData customData = this.result.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND)) return null;
        return tag.getCompound(UPGRADES_TAG);
    }

    /**
     * Copies all boolean upgrade entries from the recipe result into the output stack.
     */
    private void applyUpgrades(ItemStack stack, CompoundTag recipeUpgrades) {
        CustomData existingData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existingData != null ? existingData.copyTag() : new CompoundTag();

        CompoundTag targetUpgrades;
        if (tag.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND)) {
            targetUpgrades = tag.getCompound(UPGRADES_TAG);
        } else {
            targetUpgrades = new CompoundTag();
        }

        // Copy all upgrade keys from the recipe result
        for (String key : recipeUpgrades.getAllKeys()) {
            if (recipeUpgrades.getBoolean(key)) {
                targetUpgrades.putBoolean(key, true);
            }
        }

        tag.put(UPGRADES_TAG, targetUpgrades);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
