package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.render.RotomClientManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;

    /**
     * Durante o modo foto no F5 (visão em terceira pessoa / selfie), anima o braço e a cabeça dinamicamente:
     * - Funciona tanto para o jogador local quanto para quem vê de fora em multiplayer!
     * - Movimento livre 2D da mão: acompanha a movimentação do olhar (pitch e yaw).
     * - Mostra o movimento do braço (bobbing suave ao caminhar e respiração).
     * - O braço esquerdo balança com a caminhada livre (WASD).
     * - A cabeça segue a câmera elevada olhando diretamente na lente em todos os ângulos.
     */
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void cobblemonsmartphone$applySelfiePose(
            LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
    ) {
        if (entity instanceof Player player && RotomClientManager.INSTANCE.isPlayerSelfie(player.getUUID())) {
            float pitchRad = headPitch * ((float)Math.PI / 180F);
            float yawRad = netHeadYaw * ((float)Math.PI / 180F);

            // Braço direito estendido com movimentação livre completa (2D: pitch e yaw):
            // - Vertical: sobe e desce com pitchRad
            // - Horizontal: gira lateralmente para esquerda e direita com yawRad
            this.rightArm.xRot = -1.45F + pitchRad * 0.85F;
            this.rightArm.yRot = -0.28F + yawRad * 0.70F;
            this.rightArm.zRot = 0.22F + yawRad * 0.35F - pitchRad * 0.15F;

            // Movimento dinâmico de caminhar no braço da selfie (bobbing suave de passos)
            float walkBob = Mth.sin(limbSwing * 0.6662F) * 0.10F * limbSwingAmount;
            float walkSway = Mth.cos(limbSwing * 0.6662F) * 0.08F * limbSwingAmount;
            this.rightArm.xRot += walkBob;
            this.rightArm.zRot += walkSway;

            // Respiração sutil quando parado
            float idleBreath = Mth.sin(ageInTicks * 0.08F) * 0.025F;
            this.rightArm.xRot += idleBreath;

            if ((Object) this instanceof PlayerModel<?> playerModel) {
                playerModel.rightSleeve.copyFrom(this.rightArm);
            }

            // Braço esquerdo livre balançando naturalmente com a caminhada
            this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
            this.leftArm.yRot = 0.0F;
            this.leftArm.zRot = -0.10F + Mth.sin(limbSwing * 0.6662F) * 0.05F * limbSwingAmount;

            if ((Object) this instanceof PlayerModel<?> playerModel) {
                playerModel.leftSleeve.copyFrom(this.leftArm);
            }

            // Cabeça olhando diretamente para a câmera elevada da selfie
            this.head.xRot = pitchRad * 0.75F - 0.20F;
            this.head.yRot = 0.12F + yawRad * 0.65F;
            this.hat.copyFrom(this.head);
        }
    }
}
