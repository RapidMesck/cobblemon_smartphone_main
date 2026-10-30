package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.item.SmartphoneColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModelBakery.class)
public abstract class ModelLoaderMixin {

    @Shadow
    abstract UnbakedModel getModel(ResourceLocation resourceLocation);

    @Shadow
    protected abstract void registerModel(ModelResourceLocation modelId, UnbakedModel unbakedModel);

    @Inject(method = "<init>", at = @At("TAIL"))
    public void smartphone$init(
            BlockColors blockColors,
            ProfilerFiller profiler,
            Map<ResourceLocation, BlockModel> jsonUnbakedModels,
            Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStates,
            CallbackInfo ci
    ) {
        // Para cada cor do seu enum, registramos os 2 modelos (2D e 3D)
        for (SmartphoneColor color : SmartphoneColor.getEntries()) {
            // Modelo 2D
            ResourceLocation inventoryLocation = color.getInventoryModelPath();
            UnbakedModel unbakedInvModel = this.getModel(inventoryLocation);
            ModelResourceLocation inventoryModelId = new ModelResourceLocation(inventoryLocation, "inventory");
            this.registerModel(inventoryModelId, unbakedInvModel);

            // Modelo 3D
            ResourceLocation handLocation = color.getHandModelPath();
            UnbakedModel unbakedHandModel = this.getModel(handLocation);
            ModelResourceLocation handModelId = new ModelResourceLocation(handLocation, "inventory");
            this.registerModel(handModelId, unbakedHandModel);

            // Modelo 3D do Rotom para cada cor
            ResourceLocation rotomColorLocation = color.getRotomModelPath();
            UnbakedModel unbakedRotomColorModel = this.getModel(rotomColorLocation);
            ModelResourceLocation rotomColorModelId = new ModelResourceLocation(rotomColorLocation, "inventory");
            this.registerModel(rotomColorModelId, unbakedRotomColorModel);
        }

        // Modelo 3D do Rotom base (original / fallback)
        ResourceLocation rotomLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", "item/rotom");
        UnbakedModel unbakedRotomModel = this.getModel(rotomLocation);
        ModelResourceLocation rotomModelId = new ModelResourceLocation(rotomLocation, "inventory");
        this.registerModel(rotomModelId, unbakedRotomModel);
    }
}
