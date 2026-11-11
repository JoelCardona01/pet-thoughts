package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Something exploded: pets nearby get startled. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Inject(method = "explode", at = @At("RETURN"))
    private void petthoughts$exploded(CallbackInfoReturnable<Integer> cir) {
        ServerExplosion self = (ServerExplosion) (Object) this;
        FabricEvents.onExplosion(self.level(), self.center());
    }
}
