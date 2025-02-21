package com.nbp.cobblemon_smartphone.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nbp.cobblemon_smartphone.item.SmartphoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    /**
     * Substitui a variável 'model' antes de renderizar o item, trocando para
     * o modelo 3D caso não seja GUI/FIXED, ou mantendo o 2D se for GUI/FIXED.
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
        // Verifica se é nosso SmartphoneItem
        if (stack.getItem() instanceof SmartphoneItem smartphone) {
            // Se NÃO for GUI/FIXED (ou seja, se estiver na mão), usar modelo 3D
            if (transformType != ItemDisplayContext.GUI && transformType != ItemDisplayContext.FIXED) {
                ResourceLocation handModelLocation = smartphone.getHandModel();
                return Minecraft.getInstance()
                        .getItemRenderer()
                        .getItemModelShaper()
                        .getModelManager()
                        .getModel(new ModelResourceLocation(handModelLocation, "inventory"));
            } else {
                // Se for GUI ou FIXED, mantém o modelo 2D
                ResourceLocation invModelLocation = smartphone.getInventoryModel();
                return Minecraft.getInstance()
                        .getItemRenderer()
                        .getItemModelShaper()
                        .getModelManager()
                        .getModel(new ModelResourceLocation(invModelLocation, "inventory"));
            }
        }
        return originalModel; // Não é nosso item, mantém o modelo padrão
    }
}
