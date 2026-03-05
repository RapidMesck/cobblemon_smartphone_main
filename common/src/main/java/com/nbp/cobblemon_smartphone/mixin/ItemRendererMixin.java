package com.nbp.cobblemon_smartphone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nbp.cobblemon_smartphone.item.SmartphoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    /**
     * Intercepts the BakedModel variable before it is used in render().
     * Swaps in the 3D hand model when the item is held in first/third person.
     * GUI, GROUND, FIXED, HEAD and all other contexts keep the original 2D model.
     */
    @ModifyVariable(
            method = "render(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "ZLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private BakedModel smartphone$overrideModel(
            BakedModel originalModel,
            ItemStack stack,
            ItemDisplayContext transformType,
            boolean leftHanded,
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            int light,
            int overlay
    ) {
        if (!(stack.getItem() instanceof SmartphoneItem smartphone)) {
            return originalModel;
        }

        // Only substitute the 3D model when actually held in a hand.
        // Every other context (GUI, GROUND, FIXED, HEAD …) keeps the flat 2D icon.
        boolean isHandContext =
                transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        if (isHandContext) {
            ResourceLocation handModelLocation = smartphone.getHandModel();
            return Minecraft.getInstance()
                    .getModelManager()
                    .getModel(new ModelResourceLocation(handModelLocation, "inventory"));
        }

        return originalModel;
    }
}
