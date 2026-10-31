package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems;
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemColors.class)
public abstract class ItemColorsMixin {

    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void cobblemon_smartphone$registerRibbonColors(BlockColors blockColors, CallbackInfoReturnable<ItemColors> cir) {
        ItemColors itemColors = cir.getReturnValue();
        itemColors.register(
            (stack, tintIndex) -> tintIndex == 0 ? SmartphoneHelper.INSTANCE.getRibbonTintColor(stack) : -1,
            CobblemonSmartphoneItems.INSTANCE.getROTOM_RIBBON()
        );
    }
}
