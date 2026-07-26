package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.gui.CallOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the call overlay on top of every screen, so a ringing/active call stays visible while the
 * player is in chat, an inventory, or the smartphone UI. The HUD render hooks only fire in-world
 * (no screen), so this covers the screen-open case. CallOverlay.render is a no-op when idle.
 */
@Mixin(Screen.class)
public class ScreenRenderMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void cobblemonsmartphone$renderCallOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        CallOverlay.INSTANCE.render(guiGraphics);
    }
}
