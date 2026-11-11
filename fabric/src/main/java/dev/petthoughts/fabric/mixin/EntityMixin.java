package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.PetThoughtsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bubble visibility: "visibility: owner" and /petthoughts toggle decide who a bubble is sent to. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void petthoughts$bubbleVisibility(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Display.TextDisplay) {
            PetThoughtsMod mod = PetThoughtsMod.get();
            if (mod != null && mod.bubbleHiddenFrom((Entity) (Object) this, viewer)) {
                cir.setReturnValue(false);
            }
        }
    }
}
