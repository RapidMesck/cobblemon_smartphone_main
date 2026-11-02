package com.nbp.cobblemon_smartphone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nbp.cobblemon_smartphone.client.render.RotomClientManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    /**
     * Durante o modo selfie, oculta qualquer item que o jogador esteja segurando
     * (espada, picareta, etc.) para que a mão fique limpa segurando exclusivamente o celular/pose.
     * Funciona tanto para o jogador local quanto para outros jogadores em multiplayer!
     */
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cobblemonsmartphone$hideHeldItemDuringPhoto(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext context,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (entity instanceof Player player && RotomClientManager.INSTANCE.isPlayerSelfie(player.getUUID())) {
            ci.cancel();
        }
    }
}
