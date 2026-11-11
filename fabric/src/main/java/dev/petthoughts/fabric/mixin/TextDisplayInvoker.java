package dev.petthoughts.fabric.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {

    @Invoker("setText")
    void petthoughts$setText(Component text);

    @Invoker("setLineWidth")
    void petthoughts$setLineWidth(int width);

    @Invoker("setBackgroundColor")
    void petthoughts$setBackgroundColor(int argb);

    @Invoker("setFlags")
    void petthoughts$setFlags(byte flags);
}
