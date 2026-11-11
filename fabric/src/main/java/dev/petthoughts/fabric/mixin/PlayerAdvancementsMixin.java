package dev.petthoughts.fabric.mixin;

import dev.petthoughts.fabric.FabricEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The owner completed an advancement that is announced in chat. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award", at = @At("RETURN"))
    private void petthoughts$advancement(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && getOrStartProgress(advancement).isDone()
                && advancement.value().display().map(DisplayInfo::announceToChat).orElse(false)) {
            FabricEvents.onAdvancement(player);
        }
    }
}
