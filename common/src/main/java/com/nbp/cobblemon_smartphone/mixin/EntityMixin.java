package com.nbp.cobblemon_smartphone.mixin;

import com.nbp.cobblemon_smartphone.util.PreferencesSaver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements PreferencesSaver {
    @Unique private CompoundTag cobblemonsmartphone$savedPreferences;

    @Override
    public CompoundTag cobblemonsmartphone$getSavedPreferences() {
        if (cobblemonsmartphone$savedPreferences == null) {
            cobblemonsmartphone$savedPreferences = new CompoundTag();
        }
        return cobblemonsmartphone$savedPreferences;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    protected void injectWriteMethod(CompoundTag compoundTag, CallbackInfoReturnable<CompoundTag> cir) {
        if (cobblemonsmartphone$savedPreferences != null) {
            compoundTag.put(SAVED_PREFERENCES_KEY, cobblemonsmartphone$savedPreferences);
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    protected void injectReadMethod(CompoundTag compoundTag, CallbackInfo ci) {
        if (compoundTag.contains(SAVED_PREFERENCES_KEY)) {
            cobblemonsmartphone$savedPreferences = compoundTag.getCompound(SAVED_PREFERENCES_KEY);
        }
    }
}
