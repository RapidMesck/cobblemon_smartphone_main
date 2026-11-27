package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects mouse rotation from the local player to the Rotom Drone camera while flying.
 * Client-only mixin.
 */
@Mixin(Entity.class)
public abstract class ClientEntityTurnMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$redirectTurnToDrone(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && RotomDroneClientManager.isActive()) {
            RotomDroneClientManager.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
