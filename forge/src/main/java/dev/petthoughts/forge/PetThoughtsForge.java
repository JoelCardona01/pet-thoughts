package dev.petthoughts.forge;

import dev.petthoughts.mod.ModEvents;
import dev.petthoughts.mod.PetThoughtsMod;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * The Forge entry point. The mod lives in {@link PetThoughtsMod} (shared with the NeoForge build);
 * this class only creates it and forwards Forge's events to it.
 *
 * <p>Listeners on cancellable events are declared with their type ({@code Consumer} = only
 * watches, {@code Predicate} = returns true to cancel) because Forge's event bus accepts both and a
 * bare lambda could be read either way.
 */
@Mod(PetThoughtsMod.MOD_ID)
public final class PetThoughtsForge {

    public PetThoughtsForge(FMLJavaModLoadingContext context) {
        PetThoughtsMod mod = new PetThoughtsMod(FMLPaths.CONFIGDIR.get());

        ServerStartedEvent.BUS.addListener(event -> mod.serverStarted(event.getServer()));
        ServerStoppingEvent.BUS.addListener(event -> mod.serverStopping());
        ServerStoppedEvent.BUS.addListener(event -> mod.serverStopped());
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> mod.tick(event.server()));
        RegisterCommandsEvent.BUS.addListener(event -> mod.registerCommands(event.getDispatcher()));

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModEvents.onJoin(player);
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModEvents.onLeave(player);
            }
        });
        Consumer<EntityJoinLevelEvent> entityLoad = event -> ModEvents.onEntityLoad(event.getEntity(), event.getLevel());
        EntityJoinLevelEvent.BUS.addListener(entityLoad);

        Predicate<PlayerInteractEvent.EntityInteractSpecific> useEntity = event -> {
            if (event.getEntity() instanceof ServerPlayer player
                    && ModEvents.onUseEntity(player, event.getHand(), event.getTarget())) {
                // Petting: the click is used up here, so the pet does not sit / stand.
                event.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            return false;
        };
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(useEntity);

        Consumer<PlayerInteractEvent.RightClickBlock> useBlock = event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModEvents.onUseBlock(player, event.getHand(), event.getPos());
            }
        };
        PlayerInteractEvent.RightClickBlock.BUS.addListener(useBlock);

        Consumer<BlockEvent.BreakEvent> blockBroken = event -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                ModEvents.onBlockBroken(player, event.getState());
            }
        };
        BlockEvent.BreakEvent.BUS.addListener(blockBroken);

        // Forge reports damage just before it is applied: a hit that is about to kill is left to the death event.
        Consumer<LivingDamageEvent> damaged = event -> {
            LivingEntity entity = event.getEntity();
            if (event.getAmount() < entity.getHealth()) {
                ModEvents.onDamaged(entity, event.getSource(), event.getAmount());
            }
        };
        LivingDamageEvent.BUS.addListener(damaged);

        Consumer<LivingDeathEvent> died = event -> ModEvents.onDeath(event.getEntity(), event.getSource());
        LivingDeathEvent.BUS.addListener(died);
    }
}
