package com.nbp.cobblemon_smartphone.fabric.mixin.tomsstorage;

import com.nbp.cobblemon_smartphone.compat.tomsstorage.TomsStorageRemoteSession;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Toms Storage's own range check (canInteractWith, re-checked every tick by the terminal
 * menu's stillValid) requires the player to be physically carrying a wireless terminal item.
 * A smartphone-triggered remote session has no such item, so this short-circuits the check
 * to true for players with an active session instead - see {@link TomsStorageRemoteSession}.
 * Also covers CraftingTerminalBlockEntity, which extends this class and doesn't override the
 * method. Only applied when Toms Storage is installed - see TomsStorageMixinPlugin.
 */
@Mixin(StorageTerminalBlockEntity.class)
public class MixinStorageTerminalBlockEntity {

    @Inject(method = "canInteractWith", at = @At("HEAD"), cancellable = true)
    private void cobblemonSmartphone$bypassForRemoteSession(Player player, boolean menuCheck, CallbackInfoReturnable<Boolean> cir) {
        if (TomsStorageRemoteSession.isActive(player.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
