package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.render.RotomPlayerRenderLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cobblemon_smartphone$addRotomLayer(EntityRendererProvider.Context context, boolean useSlimModel, CallbackInfo ci) {
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        renderer.addLayer(new RotomPlayerRenderLayer(renderer));
    }
}
