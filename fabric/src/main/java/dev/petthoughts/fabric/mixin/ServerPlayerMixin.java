package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The owner finished eating something. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void petthoughts$ate(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.getUseItem().isEmpty()) {
            FabricEvents.onAte(self, self.getUseItem());
        }
    }
}
