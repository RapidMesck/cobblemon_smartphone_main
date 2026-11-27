package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the local player stationary while piloting the Rotom Drone so WASD/Space/Shift
 * drive only the drone and never move the player's body.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("RETURN"))
    private void cobblemonsmartphone$freezePlayerDuringDrone(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        if (RotomDroneClientManager.isActive()) {
            this.up = false;
            this.down = false;
            this.left = false;
            this.right = false;
            this.jumping = false;
            this.shiftKeyDown = false;
            this.forwardImpulse = 0.0F;
            this.leftImpulse = 0.0F;
        }
    }
}
