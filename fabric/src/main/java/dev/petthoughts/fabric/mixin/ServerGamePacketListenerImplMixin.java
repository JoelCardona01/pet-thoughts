package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Petting key: "swap hands" (F) while looking at your own pet pets it instead of swapping. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void petthoughts$swapKey(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        // The handler is first called on the network thread and then again on the server thread:
        // only the second call may touch the world.
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && player.level().getServer().isSameThread()
                && FabricEvents.onSwapKey(player)) {
            ci.cancel();
        }
    }
}
