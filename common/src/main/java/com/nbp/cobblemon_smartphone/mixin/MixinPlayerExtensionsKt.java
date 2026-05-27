package com.nbp.cobblemon_smartphone.mixin;

import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.nbp.cobblemon_smartphone.client.scanner.ScannerManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerExtensionsKt.class, remap = false)
public class MixinPlayerExtensionsKt {

    @Inject(method = "isUsingPokedex", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onIsUsingPokedex(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (ScannerManager.INSTANCE.isInUse()) {
            cir.setReturnValue(true);
        }
    }
}
