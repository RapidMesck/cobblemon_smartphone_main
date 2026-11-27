package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents attacking, mining, using items/blocks, or pick-blocking while piloting the Rotom Drone.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$blockAttackInDrone(CallbackInfoReturnable<Boolean> cir) {
        if (RotomDroneClientManager.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$blockContinueAttackInDrone(boolean leftClick, CallbackInfo ci) {
        if (RotomDroneClientManager.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$blockUseItemInDrone(CallbackInfo ci) {
        if (RotomDroneClientManager.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$blockPickBlockInDrone(CallbackInfo ci) {
        if (RotomDroneClientManager.isActive()) {
            ci.cancel();
        }
    }
}
