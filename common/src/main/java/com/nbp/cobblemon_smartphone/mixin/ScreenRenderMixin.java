package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.gui.CallOverlay;
import com.nbp.cobblemon_smartphone.client.gui.GpsCompassOverlay;
import com.nbp.cobblemon_smartphone.client.gui.StructureCompassOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the call overlay and the GPS/structure trackers on top of every screen, so a ringing/active
 * call or an active track stays visible while the player is in chat, an inventory, or the
 * smartphone UI. The HUD render hooks only fire in-world (no screen), so this covers the
 * screen-open case. All renders are no-ops when idle.
 */
@Mixin(Screen.class)
public class ScreenRenderMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void cobblemonsmartphone$renderOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        CallOverlay.INSTANCE.render(guiGraphics);
        GpsCompassOverlay.INSTANCE.render(guiGraphics);
        StructureCompassOverlay.INSTANCE.render(guiGraphics);
    }
}
