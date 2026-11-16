package dev.petthoughts.mod.mixin;

import dev.petthoughts.mod.ModEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Horses, donkeys, mules and llamas are tamed through their own method. */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {

    @Inject(method = "tameWithName", at = @At("RETURN"))
    private void petthoughts$tamed(Player player, CallbackInfoReturnable<Boolean> cir) {
        ModEvents.onTamed((Entity) (Object) this, player);
    }
}
