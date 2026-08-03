package com.nbp.cobblemon_smartphone.fabric.mixin.tomsstorage;

import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageRemoteSession;
import com.tom.storagemod.Config;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Toms Storage's own range check (canInteractWith, re-checked every tick by the terminal
 * menu's stillValid) normally requires the player to be physically carrying a wireless
 * terminal item, with range beyond a few blocks only available via the terminal's own
 * beacon tiers (Config.wirelessTermBeaconLvl for infinite same-dimension range,
 * wirelessTermBeaconLvlCrossDim for infinite cross-dimension range). A smartphone-triggered
 * remote session has no such item, so this replicates those same beacon-tier rules using the
 * session's own data (see TomsStorageRemoteSession) instead of granting unconditional access
 * - the smartphone gets exactly the range a real Advanced Wireless Terminal would get for the
 * same beacon setup, no more. When there's no adequate beacon, this doesn't set a return
 * value at all, so the method falls through to Toms Storage's normal (short-range) check.
 * Also covers CraftingTerminalBlockEntity, which extends this class and doesn't override the
 * method. Only applied when Toms Storage is installed - see TomsStorageMixinPlugin.
 */
@Mixin(StorageTerminalBlockEntity.class)
public class MixinStorageTerminalBlockEntity {

    @Shadow
    public int getBeaconLevel() {
        throw new UnsupportedOperationException();
    }

    @Inject(method = "canInteractWith", at = @At("HEAD"), cancellable = true)
    private void cobblemonSmartphone$bypassForRemoteSession(Player player, boolean menuCheck, CallbackInfoReturnable<Boolean> cir) {
        if (!TomsStorageRemoteSession.isActive(player.getUUID())) {
            return;
        }

        int beaconLevel = getBeaconLevel();
        int sameDimensionLevel = Config.get().wirelessTermBeaconLvl;
        int crossDimensionLevel = Config.get().wirelessTermBeaconLvlCrossDim;

        if (sameDimensionLevel == -1 || beaconLevel < sameDimensionLevel) {
            // No adequate beacon - fall through to Toms Storage's own short-range check.
            return;
        }

        if (crossDimensionLevel != -1 && beaconLevel >= crossDimensionLevel) {
            cir.setReturnValue(true);
            return;
        }

        cir.setReturnValue(TomsStorageRemoteSession.isBoundDimension(player.getUUID(), player.level().dimension()));
    }
}
