package dev.petthoughts.fabric;

import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.command.Commands;
import dev.petthoughts.core.config.DataFiles;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.config.Settings;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import dev.petthoughts.core.platform.Platform;
import dev.petthoughts.core.thought.PetEvents;
import dev.petthoughts.core.thought.ThoughtDirector;
import dev.petthoughts.core.thought.ThoughtDirector.Friend;
import dev.petthoughts.core.bubble.Bubble;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Fabric mod: wires vanilla/Fabric events to the shared brain in the common module.
 *
 * <p>All the logic is server-side, so it works in single player (integrated server) and on Fabric
 * servers with vanilla clients.
 */
public final class PetThoughtsMod implements ModInitializer, Platform {

    public static final String MOD_ID = "petthoughts";
    private static final Logger LOGGER = LoggerFactory.getLogger("PetThoughts");
    private static final int SCAN_PERIOD = 40;
    private static final double SCAN_RADIUS = 32;

    private static PetThoughtsMod instance;

    private record Delayed(LivingEntity pet, long dueTick, Runnable action) {}

    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private DataFiles files;
    private ThoughtDirector director;
    private PetEvents events;
    private FabricBubbles bubbles;
    private MinecraftServer server;
    private long tick;

    /** The running mod; used by mixins, which cannot receive it any other way. */
    public static PetThoughtsMod get() {
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        FabricPet.init();
        files = new DataFiles(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID),
                PetThoughtsMod.class.getClassLoader(), LOGGER::warn);
        director = new ThoughtDirector(this);
        events = new PetEvents(this, director);
        bubbles = new FabricBubbles(this);

        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            bubbles.clear();
            delayed.clear();
            director.reset();
            hiddenPlayers.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
        FabricEvents.register(this);
        Commands commands = new Commands(this, director);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                FabricCommand.register(dispatcher, this, commands));
    }

    // ------------------------------------------------------------------
    //  Ticking: delayed reactions, bubbles and the pet scan
    // ------------------------------------------------------------------

    private void onTick(MinecraftServer running) {
        tick++;
        if (!delayed.isEmpty()) {
            List<Delayed> due = new ArrayList<>();
            for (Iterator<Delayed> it = delayed.iterator(); it.hasNext(); ) {
                Delayed entry = it.next();
                if (entry.dueTick <= tick) {
                    it.remove();
                    due.add(entry);
                }
            }
            for (Delayed entry : due) {
                if (entry.pet.isAlive() && !entry.pet.isRemoved()) {
                    entry.action.run();
                }
            }
        }
        bubbles.tick();
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            // Spread players over the 2-second period instead of scanning everyone on the same tick.
            if ((tick + player.getId()) % SCAN_PERIOD == 0) {
                scan(player);
            }
        }
        if (tick % (20 * 60 * 10) == 0) {
            director.purge();
        }
    }

    /** Looks for the player's own pets nearby. The world is never iterated: no players, no work. */
    private void scan(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || settings().worldDisabled(FabricPet.worldId(player.level()))) {
            return;
        }
        OwnerView owner = FabricPet.owner(player);
        List<PetView> pets = petsNear(owner, player.getX(), player.getY(), player.getZ(), SCAN_RADIUS);
        if (pets.isEmpty()) {
            return;
        }
        List<Friend> friends = new ArrayList<>(pets.size());
        for (PetView pet : pets) {
            friends.add(Friend.of(pet));
        }
        for (PetView pet : pets) {
            director.evaluate(pet, owner, friends);
        }
    }

    // ------------------------------------------------------------------
    //  Platform
    // ------------------------------------------------------------------

    @Override
    public Settings settings() {
        return files.settings();
    }

    @Override
    public LangStore lang() {
        return files.lang();
    }

    @Override
    public void log(String message) {
        LOGGER.info(message);
    }

    @Override
    public void reload() {
        files.reload();
        bubbles.clear();
        delayed.clear();
        director.reset();
    }

    @Override
    public Trigger.Tier bubbleTier(UUID pet) {
        return bubbles.tierOf(pet);
    }

    @Override
    public int bubbleCount(UUID owner) {
        return bubbles.countFor(owner);
    }

    @Override
    public void showBubble(PetView pet, OwnerView owner, Bubble bubble) {
        if (isHidden(owner.id()) && (settings().ownerOnly || isAlone())) {
            return; // nobody would see it
        }
        bubbles.show(((FabricPet) pet).entity(), pet.species().rideable(), pet.species().bubbleGap(), owner.id(),
                bubble, FabricText.bubble(bubble));
    }

    private boolean isAlone() {
        return server != null && server.getPlayerList().getPlayers().size() <= 1;
    }

    @Override
    public void removeBubble(UUID pet) {
        bubbles.remove(pet);
    }

    @Override
    public void later(PetView pet, int ticks, Runnable action) {
        delayed.add(new Delayed(((FabricPet) pet).entity(), tick + Math.max(1, ticks), action));
    }

    @Override
    public List<PetView> petsNear(OwnerView owner, double x, double y, double z, double radius) {
        List<PetView> pets = new ArrayList<>();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner.id());
        if (player == null) {
            return pets;
        }
        ServerLevel level = player.level();
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (Entity entity : level.getEntities((Entity) null, box, candidate -> FabricPet.speciesOf(candidate) != null)) {
            FabricPet pet = FabricPet.of(this, entity);
            if (pet != null && owner.id().equals(pet.ownerId()) && settings().speciesEnabled(pet.species())) {
                pets.add(pet);
            }
        }
        return pets;
    }

    // ------------------------------------------------------------------

    public MinecraftServer server() {
        return server;
    }

    public ThoughtDirector director() {
        return director;
    }

    public PetEvents events() {
        return events;
    }

    FabricBubbles bubbles() {
        return bubbles;
    }

    /** Used by the visibility mixin. */
    public boolean bubbleHiddenFrom(Entity entity, ServerPlayer viewer) {
        return bubbles.hiddenFrom(entity, viewer);
    }

    public boolean isHidden(UUID player) {
        return hiddenPlayers.contains(player);
    }

    void setHidden(UUID player, boolean hidden) {
        if (hidden) {
            hiddenPlayers.add(player);
        } else {
            hiddenPlayers.remove(player);
        }
    }
}
