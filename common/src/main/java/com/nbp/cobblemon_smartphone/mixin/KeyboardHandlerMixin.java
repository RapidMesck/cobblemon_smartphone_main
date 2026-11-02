package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager;
import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets ESC close the pokedex scan overlay or the social camera capture viewfinder,
 * so a smartphone user who opened the scanner or camera is never stuck in it.
 * While the scanner or camera is in use (and no screen is open) the ESC press is consumed.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$closeScannerOrCameraOnEscape(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE
                && Minecraft.getInstance().screen == null) {
            if (ScannerManager.INSTANCE.isInUse()) {
                ScannerManager.INSTANCE.deactivate();
                ci.cancel();
            } else if (SocialPhotoClient.INSTANCE.isCameraActive()) {
                SocialPhotoClient.INSTANCE.cancelCamera();
                ci.cancel();
            }
        }
    }
}
