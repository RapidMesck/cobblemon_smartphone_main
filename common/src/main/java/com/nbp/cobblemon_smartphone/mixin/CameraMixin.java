package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.client.social.SocialPhotoClient;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    /**
     * Quando o jogador estiver tirando uma foto social no F5 (visão em terceira pessoa / selfie),
     * ancora a câmera na mão do jogador segurando o smartphone:
     * - Câmera elevada (~24cm acima da mão), simulando a lente no topo do smartphone para que a mão
     *   fique recortada na borda inferior como uma selfie real.
     * - Movimento livre 2D: responde tanto ao pitch (cima/baixo) quanto ao yaw lateral (esquerda/direita).
     * - Enquadra o jogador posicionado mais à esquerda do visor (regra dos terços).
     * - Acompanha a movimentação livre pelo mundo (WASD).
     */
    @Inject(method = "setup", at = @At("RETURN"))
    private void cobblemonsmartphone$applySelfieCamera(
            BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci
    ) {
        if (SocialPhotoClient.INSTANCE.isCameraActive() && detached && entity instanceof Player player) {
            double px = Mth.lerp(partialTicks, player.xOld, player.getX());
            double py = Mth.lerp(partialTicks, player.yOld, player.getY());
            double pz = Mth.lerp(partialTicks, player.zOld, player.getZ());
            double eyeY = py + player.getEyeHeight(player.getPose());

            // Ângulos de visão do jogador controlados pelo mouse
            float viewYaw = player.getViewYRot(partialTicks);
            float viewPitch = player.getViewXRot(partialTicks);

            // Ângulo horizontal relativo entre a cabeça e o corpo
            float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
            float relYaw = Mth.wrapDegrees(viewYaw - bodyYaw);

            double yawRad = Math.toRadians(viewYaw);
            double pitchRad = Math.toRadians(viewPitch);
            double relYawRad = Math.toRadians(relYaw);

            // Vetores direcionais no plano horizontal baseados no olhar do jogador
            double fwdX = -Math.sin(yawRad);
            double fwdZ = Math.cos(yawRad);
            double rightX = Math.cos(yawRad);
            double rightZ = Math.sin(yawRad);

            // Posição do ombro direito no espaço de mundo
            double shoulderX = px + rightX * 0.32;
            double shoulderY = eyeY - 0.22;
            double shoulderZ = pz + rightZ * 0.32;

            // Alcance dinâmico 2D da mão:
            // - Verticalmente: sobe ao olhar para cima, desce ao olhar para baixo
            // - Horizontalmente: move para esquerda/direita conforme o mouse gira lateralmente
            double lateralReach = -0.06 + Math.sin(relYawRad) * 0.42;
            double fwdReach = (0.72 * Math.cos(pitchRad)) * Math.cos(relYawRad * 0.5);
            double reachY = 0.10 - Math.sin(pitchRad) * 0.55;

            double handX = shoulderX + fwdX * fwdReach + rightX * lateralReach;
            double handY = shoulderY + reachY;
            double handZ = shoulderZ + fwdZ * fwdReach + rightZ * lateralReach;

            // Vetor de extensão do braço
            double extX = handX - shoulderX;
            double extY = handY - shoulderY;
            double extZ = handZ - shoulderZ;
            double extLen = Math.sqrt(extX * extX + extY * extY + extZ * extZ);
            if (extLen < 0.001) extLen = 1.0;
            double normExtX = extX / extLen;
            double normExtY = extY / extLen;
            double normExtZ = extZ / extLen;

            // Câmera posicionada no topo do celular, elevada ~24cm acima da mão
            // Isso oculta o punho da tela deixando apenas o braço estendido visível na borda inferior
            double camDistPastHand = 0.22;
            double camElev = 0.24;
            double rawCamX = handX + normExtX * camDistPastHand;
            double rawCamY = handY + normExtY * camDistPastHand + camElev;
            double rawCamZ = handZ + normExtZ * camDistPastHand;

            // Ponto focal: centro do rosto do jogador
            double targetX = px;
            double targetY = eyeY - 0.02;
            double targetZ = pz;

            // Raycast contra blocos para evitar que a câmera atravesse paredes ou tetos
            Vec3 targetVec = new Vec3(targetX, targetY, targetZ);
            Vec3 rawCamVec = new Vec3(rawCamX, rawCamY, rawCamZ);
            BlockHitResult hit = level.clip(new ClipContext(
                    targetVec,
                    rawCamVec,
                    ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE,
                    entity
            ));

            Vec3 finalCamPos;
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 hitPos = hit.getLocation();
                double hdx = targetX - hitPos.x;
                double hdy = targetY - hitPos.y;
                double hdz = targetZ - hitPos.z;
                double hlen = Math.sqrt(hdx * hdx + hdy * hdy + hdz * hdz);
                if (hlen > 0.35) {
                    finalCamPos = hitPos.add((hdx / hlen) * 0.12, (hdy / hlen) * 0.12, (hdz / hlen) * 0.12);
                } else {
                    finalCamPos = hitPos;
                }
            } else {
                finalCamPos = rawCamVec;
            }

            // Direção da câmera em direção ao jogador
            double dirX = targetX - finalCamPos.x;
            double dirY = targetY - finalCamPos.y;
            double dirZ = targetZ - finalCamPos.z;
            double distXZ = Math.sqrt(dirX * dirX + dirZ * dirZ);

            float baseYaw = (float)(Mth.atan2(dirZ, dirX) * (180.0 / Math.PI)) - 90.0F;
            float basePitch = (float)(-(Mth.atan2(dirY, distXZ) * (180.0 / Math.PI)));

            // Enquadramento: gira a câmera ligeiramente para a direita (+13.0°),
            // posicionando o jogador naturalmente mais para a esquerda da tela (regra dos terços).
            float camYaw = baseYaw + 13.0F;
            float camPitch = basePitch;

            this.setRotation(camYaw, camPitch);
            this.setPosition(finalCamPos);
        }
    }
}
