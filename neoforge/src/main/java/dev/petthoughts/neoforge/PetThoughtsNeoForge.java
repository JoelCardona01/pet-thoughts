package dev.petthoughts.neoforge;

import dev.petthoughts.mod.ModEvents;
import dev.petthoughts.mod.PetThoughtsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The NeoForge entry point. The mod lives in {@link PetThoughtsMod} (shared with the Forge build);
 * this class only creates it and forwards NeoForge's events to it.
 */
@Mod(PetThoughtsMod.MOD_ID)
public final class PetThoughtsNeoForge {

    public PetThoughtsNeoForge() {
        PetThoughtsMod mod = new PetThoughtsMod(FMLPaths.CONFIGDIR.get());
        IEventBus bus = NeoForge.EVENT_BUS;

        bus.addListener((ServerStartedEvent event) -> mod.serverStarted(event.getServer()));
        bus.addListener((ServerStoppingEvent event) -> mod.serverStopping());
        bus.addListener((ServerStoppedEvent event) -> mod.serverStopped());
        bus.addListener((ServerTickEvent.Post event) -> mod.tick(event.getServer()));
        bus.addListener((RegisterCommandsEvent event) -> mod.registerCommands(event.getDispatcher()));

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModEvents.onJoin(player);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModEvents.onLeave(player);
            }
        });
        bus.addListener((EntityJoinLevelEvent event) -> ModEvents.onEntityLoad(event.getEntity(), event.getLevel()));

        bus.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (event.getEntity() instanceof ServerPlayer player
                    && ModEvents.onUseEntity(player, event.getHand(), event.getTarget())) {
                // Petting: the click is used up here, so the pet does not sit / stand.
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModEvents.onUseBlock(player, event.getHand(), event.getPos());
            }
        });
        bus.addListener((BreakBlockEvent event) -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                ModEvents.onBlockBroken(player, event.getState());
            }
        });

        bus.addListener((LivingDamageEvent.Post event) ->
                ModEvents.onDamaged(event.getEntity(), event.getSource(), event.getInflictedDamage()));
        bus.addListener((LivingDeathEvent event) -> ModEvents.onDeath(event.getEntity(), event.getSource()));
    }
}
