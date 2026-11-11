package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Two animals just had a baby. */
@Mixin(Animal.class)
public abstract class AnimalMixin {

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
    private void petthoughts$bred(ServerLevel level, Animal partner, AgeableMob baby, CallbackInfo ci) {
        FabricEvents.onBred((Entity) (Object) this, partner);
    }
}
