package dev.petthoughts.fabric.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla keeps the Display setters private (they are meant for NBT); the bubbles need them. */
@Mixin(Display.class)
public interface DisplayInvoker {

    @Invoker("setTransformation")
    void petthoughts$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void petthoughts$setInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void petthoughts$setInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void petthoughts$setTeleportDuration(int ticks);

    @Invoker("setBillboardConstraints")
    void petthoughts$setBillboard(Display.BillboardConstraints constraints);

    @Invoker("setBrightnessOverride")
    void petthoughts$setBrightness(Brightness brightness);

    @Invoker("setViewRange")
    void petthoughts$setViewRange(float range);
}
