package com.nbp.cobblemon_smartphone.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager
import com.nbp.cobblemon_smartphone.client.RotomScannerClientState
import com.nbp.cobblemon_smartphone.item.SmartphoneColor
import com.nbp.cobblemon_smartphone.item.SmartphoneItem
import com.nbp.cobblemon_smartphone.registry.CobblemonSmartphoneItems
import com.nbp.cobblemon_smartphone.skin.SmartphoneSkinHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.getRotomAccessory
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper.setRotomPhone
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

object RotomDroneRenderer {

    @JvmStatic
    fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        camera: Camera,
        partialTick: Float
    ) {
        if (!RotomScannerClientState.clientConfig.enableRotom || !RotomScannerClientState.clientConfig.enableDrone) return
        val drones = RotomDroneClientManager.getRemoteDrones()
        if (drones.isEmpty()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camPos = camera.position
        val time = (level.gameTime % 24000L).toFloat() + partialTick

        try {
            for (drone in drones) {
                val x = Mth.lerp(partialTick.toDouble(), drone.prevX, drone.currX)
                val y = Mth.lerp(partialTick.toDouble(), drone.prevY, drone.currY)
                val z = Mth.lerp(partialTick.toDouble(), drone.prevZ, drone.currZ)
                val yaw = Mth.rotLerp(partialTick, drone.prevYaw, drone.currYaw)
                val pitch = Mth.lerp(partialTick, drone.prevPitch, drone.currPitch)

                val owner = level.getPlayerByUUID(drone.playerUuid)
                val smartphoneStack = (if (owner != null) RotomClientManager.getEffectiveSmartphone(owner) else null)
                    ?: ItemStack(CobblemonSmartphoneItems.RED_SMARTPHONE).also { it.setRotomPhone(true) }
                val color = (smartphoneStack.item as? SmartphoneItem)?.getColor() ?: SmartphoneColor.RED

                val customLoc = ModelResourceLocation(
                    SmartphoneSkinHelper.resolveHandModel(smartphoneStack, color, true),
                    "inventory"
                )
                val customBaked = mc.modelManager.getModel(customLoc)
                val bakedModel = if (customBaked != mc.modelManager.missingModel) {
                    customBaked
                } else {
                    mc.modelManager.getModel(
                        RotomPlayerRenderLayer.ROTOM_COLOR_MODELS[color] ?: RotomPlayerRenderLayer.ROTOM_MODEL_LOCATION
                    )
                }
                if (bakedModel == mc.modelManager.missingModel) continue

                val packedLight = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z))
                val bobY = Mth.sin(time * 0.16f) * 0.055

                poseStack.pushPose()
                poseStack.translate(x - camPos.x, y - camPos.y + bobY, z - camPos.z)
                // Face the direction the drone camera is looking
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(-pitch * 0.65f))
                poseStack.scale(0.85f, 0.85f, 0.85f)

                mc.itemRenderer.render(
                    smartphoneStack,
                    ItemDisplayContext.NONE,
                    false,
                    poseStack,
                    bufferSource,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    bakedModel
                )

                // Render optional Rotom accessory (bow / bowtie)
                val accessory = smartphoneStack.getRotomAccessory()
                if (accessory != null) {
                    val accessoryStack = SmartphoneHelper.createAccessoryStackFromPhone(smartphoneStack)
                    if (accessoryStack != null) {
                        val accBaked = mc.itemRenderer.getModel(accessoryStack, level, owner, 0)
                        poseStack.pushPose()
                        if (accessory == "bow") {
                            poseStack.translate(0.096, -0.016, 0.065)
                            poseStack.scale(0.22f, 0.22f, 0.22f)
                            poseStack.mulPose(Axis.ZP.rotationDegrees(-18.0f))
                        } else {
                            poseStack.translate(0.0, -0.24, 0.065)
                            poseStack.scale(0.22f, 0.22f, 0.22f)
                        }
                        mc.itemRenderer.render(
                            accessoryStack,
                            ItemDisplayContext.NONE,
                            false,
                            poseStack,
                            bufferSource,
                            packedLight,
                            OverlayTexture.NO_OVERLAY,
                            accBaked
                        )
                        poseStack.popPose()
                    }
                }

                poseStack.popPose()
            }
        } catch (_: Throwable) {
            // Prevent any render exception from breaking the level render loop
        }
    }
}
