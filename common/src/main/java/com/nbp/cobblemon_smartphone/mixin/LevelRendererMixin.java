package com.nbp.cobblemon_smartphone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nbp.cobblemon_smartphone.client.render.RotomPlayerRenderLayer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void cobblemon_smartphone$onRenderLevelHead(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        RotomPlayerRenderLayer.setRenderingWorld(true);
    }

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            ordinal = 0
        )
    )
    private void cobblemon_smartphone$renderFirstPersonRotom(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        if (!camera.isDetached()) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            RotomPlayerRenderLayer.renderFirstPersonForLocalPlayer(
                new PoseStack(),
                this.renderBuffers.bufferSource(),
                camera,
                partialTick
            );
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void cobblemon_smartphone$onRenderLevelReturn(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        RotomPlayerRenderLayer.setRenderingWorld(false);
    }
}
