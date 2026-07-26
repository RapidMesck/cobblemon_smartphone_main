package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.gui.CallOverlay;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes a left mouse press to the in-world call overlay before the game handles it, so the
 * overlay's Accept/Decline/Hang-up buttons are clickable while no screen is open (the HUD never
 * receives clicks on its own). Consumes the press only when the overlay actually handled it.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$callOverlayClick(long window, int button, int action, int mods, CallbackInfo ci) {
        // GLFW_MOUSE_BUTTON_LEFT == 0, GLFW_PRESS == 1
        if (button == 0 && action == 1 && CallOverlay.INSTANCE.handleClick()) {
            ci.cancel();
        }
    }
}
