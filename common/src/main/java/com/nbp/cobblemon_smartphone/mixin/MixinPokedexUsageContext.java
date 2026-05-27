package com.nbp.cobblemon_smartphone.mixin;

import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext;
import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokedexUsageContext.class, remap = false)
public class MixinPokedexUsageContext {

    @Inject(method = "resetState", at = @At("HEAD"), cancellable = true, remap = false)
    private void onResetState(boolean resetAnimationStates, CallbackInfo ci) {
        if (ScannerManager.INSTANCE.isInUse() && !resetAnimationStates) {
            ci.cancel();
        }
    }

    @Inject(method = "stopUsing", at = @At("HEAD"), cancellable = true, remap = false)
    private void onStopUsing(int ticksInUse, ResourceLocation speciesId, CallbackInfo ci) {
        if (ScannerManager.INSTANCE.isInUse()) {
            ci.cancel();
        }
    }
}
