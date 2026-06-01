package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.item.SmartphoneItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingTransformRecipe.class)
public class MixinSmithingTransformRecipe {

    private static final String UPGRADES_TAG = "cobblemon_smartphone:upgrades";

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    public void onAssemble(SmithingRecipeInput input, HolderLookup.Provider registries,
                           CallbackInfoReturnable<ItemStack> cir) {
        ItemStack base = input.base();

        // Only intercept if the base is a smartphone
        if (!(base.getItem() instanceof SmartphoneItem)) {
            return;
        }

        // Determine which upgrade to apply based on the addition item
        String upgradeKey = getUpgradeFromAddition(input.addition());
        if (upgradeKey == null) {
            return;
        }

        // Copy the base smartphone (preserves color) and add the upgrade NBT
        ItemStack result = base.copy();
        result.setCount(1);
        addUpgradeNbt(result, upgradeKey);

        cir.setReturnValue(result);
    }

    private String getUpgradeFromAddition(ItemStack addition) {
        if (addition.isEmpty()) return null;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(addition.getItem());

        // PokeNav: matches cobblenav:pokenav_item_* except pokenav_item_old
        if ("cobblenav".equals(id.getNamespace())
            && id.getPath().startsWith("pokenav_item_")
            && !id.getPath().equals("pokenav_item_old")) {
            return "upgrade_pokenav";
        }

        // Waystone: matches waystones:*_warp_stone
        if ("waystones".equals(id.getNamespace())
            && id.getPath().endsWith("warp_stone")) {
            return "upgrade_waystone";
        }

        return null;
    }

    private void addUpgradeNbt(ItemStack stack, String nbtKey) {
        CustomData existingData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existingData != null ? existingData.copyTag() : new CompoundTag();

        CompoundTag upgrades;
        if (tag.contains(UPGRADES_TAG, CompoundTag.TAG_COMPOUND)) {
            upgrades = tag.getCompound(UPGRADES_TAG);
        } else {
            upgrades = new CompoundTag();
        }

        upgrades.putBoolean(nbtKey, true);
        tag.put(UPGRADES_TAG, upgrades);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
