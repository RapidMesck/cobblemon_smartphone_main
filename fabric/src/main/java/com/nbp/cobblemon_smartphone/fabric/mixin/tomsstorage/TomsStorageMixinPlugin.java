package com.nbp.cobblemon_smartphone.fabric.mixin.tomsstorage;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Toms Storage is an optional dependency, so this whole mixin config must be a no-op when
 * the mod isn't installed. shouldApplyMixin checks mod presence via FabricLoader - never
 * Class.forName/reflection on the target class itself, which would force it (and its whole
 * superclass chain, including vanilla BlockEntity) to load before Mixin has had a chance to
 * transform it, breaking other mods' mixins on the same classes
 * (MixinTargetAlreadyLoadedException).
 */
public class TomsStorageMixinPlugin implements IMixinConfigPlugin {
    private static final String MOD_ID = "toms_storage";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
