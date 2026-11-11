package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wolves, cats and parrots: "just tamed" and "teleported to my owner". */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {

    @Inject(method = "tame", at = @At("TAIL"))
    private void petthoughts$tamed(Player player, CallbackInfo ci) {
        FabricEvents.onTamed((Entity) (Object) this, player);
    }

    @Inject(method = "maybeTeleportTo", at = @At("RETURN"))
    private void petthoughts$teleported(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            FabricEvents.onTeleported((Entity) (Object) this);
        }
    }
}
