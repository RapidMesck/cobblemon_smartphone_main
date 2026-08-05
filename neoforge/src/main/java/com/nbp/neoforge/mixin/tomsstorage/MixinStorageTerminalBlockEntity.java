package com.nbp.neoforge.mixin.tomsstorage;

import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageRemoteSession;
import com.tom.storagemod.Config;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import com.tom.storagemod.util.BeaconLevelCalc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Toms Storage's own range check (canInteractWith, re-checked every tick by the terminal
 * menu's stillValid) normally requires the player to be physically carrying a wireless
 * terminal item, with range beyond a few blocks only available via the terminal's own
 * beacon tiers (Config.wirelessTermBeaconLvl for infinite same-dimension range,
 * wirelessTermBeaconLvlCrossDim for infinite cross-dimension range). A smartphone-triggered
 * remote session has no such item, so same-dimension access is handled by the session itself
 * and cross-dimension access still uses the configured beacon tier.
 * Also covers CraftingTerminalBlockEntity, which extends this class and doesn't override the
 * method. Only applied when Toms Storage is installed - see TomsStorageMixinPlugin.
 */
@Mixin(StorageTerminalBlockEntity.class)
public class MixinStorageTerminalBlockEntity {

    @Inject(method = "canInteractWith", at = @At("HEAD"), cancellable = true)
    private void cobblemonSmartphone$bypassForRemoteSession(Player player, boolean menuCheck, CallbackInfoReturnable<Boolean> cir) {
        // The remote session is server-owned. On a dedicated server the client
        // cannot see that session and would run Toms' original inventory check,
        // closing the otherwise valid remote menu as "out of range". The server
        // below remains authoritative for beacon, dimension and distance rules.
        if (player.level().isClientSide) {
            cir.setReturnValue(true);
            return;
        }

        if (!TomsStorageRemoteSession.isActive(player.getUUID())) {
            return;
        }

        StorageTerminalBlockEntity terminal = (StorageTerminalBlockEntity) (Object) this;
        int beaconLevel = cobblemonSmartphone$beaconLevel(terminal);
        boolean sameDimension = TomsStorageRemoteSession.isBoundDimension(
            player.getUUID(), player.level().dimension()
        );
        int sameDimensionLevel = Config.get().wirelessTermBeaconLvl;
        int crossDimensionLevel = Config.get().wirelessTermBeaconLvlCrossDim;

        if (sameDimension && sameDimensionLevel != -1 && beaconLevel >= sameDimensionLevel) {
            cir.setReturnValue(true);
            return;
        }

        if (crossDimensionLevel != -1 && beaconLevel >= crossDimensionLevel) {
            cir.setReturnValue(true);
            return;
        }

        // No adequate beacon: replicate the Advanced Wireless Terminal's normal
        // range, without requiring a second physical wireless terminal in the
        // inventory. Toms adds one quarter during the menu validity check.
        int range = Math.max(6, Config.get().advWirelessRange);
        if (menuCheck) range += range / 4;
        cir.setReturnValue(
            sameDimension && player.distanceToSqr(
                terminal.getBlockPos().getX() + 0.5D,
                terminal.getBlockPos().getY() + 0.5D,
                terminal.getBlockPos().getZ() + 0.5D
            ) <= (double) range * range
        );
    }

    private static int cobblemonSmartphone$beaconLevel(StorageTerminalBlockEntity terminal) {
        Level level = terminal.getLevel();
        BlockPos center = terminal.getBlockPos();
        if (level == null) return -1;
        return BlockPos.betweenClosedStream(new AABB(center).inflate(8.0D))
            .mapToInt(pos -> BeaconLevelCalc.calcBeaconLevel(level, pos.getX(), pos.getY(), pos.getZ()))
            .max()
            .orElse(-1);
    }
}
