package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets ESC close the pokedex scan overlay, so a smartphone user who opened the scanner is never
 * stuck in it. While the scanner is in use (and no screen is open) the ESC press is consumed:
 * the scanner deactivates instead of the pause menu opening on top of it.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$closeScannerOnEscape(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE
                && ScannerManager.INSTANCE.isInUse()
                && Minecraft.getInstance().screen == null) {
            ScannerManager.INSTANCE.deactivate();
            ci.cancel();
        }
    }
}
