package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The owner reeled in a catch (vanilla returns 1 rod damage for a caught fish or treasure). */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @Inject(method = "retrieve", at = @At("RETURN"))
    private void petthoughts$fished(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() == 1) {
            FabricEvents.onFished(((FishingHook) (Object) this).getPlayerOwner());
        }
    }
}
