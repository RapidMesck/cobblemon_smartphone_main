package com.nbp.cobblemon_smartphone.mixin;

import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.StartScanningPacket;
import com.cobblemon.mod.common.net.serverhandling.pokedex.scanner.StartScanningHandler;
import com.cobblemon.mod.common.pokedex.scanner.PlayerScanningDetails;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.nbp.cobblemon_smartphone.CobblemonSmartphone;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StartScanningHandler.class, remap = false)
public class MixinStartScanningHandler {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void onHandle(StartScanningPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (!CobblemonSmartphone.INSTANCE.getConfig().getFeatures().getEnableScanner()
                && !PlayerExtensionsKt.isUsingPokedex(player)) {
            PlayerScanningDetails.INSTANCE.getPlayerToEntityMap().remove(player.getUUID());
            PlayerScanningDetails.INSTANCE.getPlayerToTickMap().remove(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("message.nbp.scanner.disabled").withColor(0xfd0100),
                    true
            );
            ci.cancel();
        }
    }
}
