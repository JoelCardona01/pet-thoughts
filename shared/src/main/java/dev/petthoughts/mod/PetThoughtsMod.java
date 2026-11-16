package dev.petthoughts.mod;

import com.mojang.brigadier.CommandDispatcher;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod itself, shared by the NeoForge and the Forge builds: connects the game to the brain in
 * the common module. It only touches vanilla classes; each loader has one small class that creates
 * it and forwards the loader's events to the {@code server*}, {@link #tick} and
 * {@link #registerCommands} methods here and to the handlers in {@link ModEvents}.
 *
 * <p>All the logic is server-side, so it works in single player (integrated server) and on modded
 * servers with vanilla clients.
 */
public final class PetThoughtsMod implements Platform {

    public static final String MOD_ID = "petthoughts";
    private static final Logger LOGGER = LoggerFactory.getLogger("PetThoughts");
    private static final int SCAN_PERIOD = 40;
    private static final double SCAN_RADIUS = 32;

    private static PetThoughtsMod instance;

    private record Delayed(LivingEntity pet, long dueTick, Runnable action) {}

    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final DataFiles files;
    private final ThoughtDirector director;
    private final PetEvents events;
    private final ModBubbles bubbles;
    private final Commands commands;
    private MinecraftServer server;
    private long tick;

    /** The running mod; used by mixins, which cannot receive it any other way. */
    public static PetThoughtsMod get() {
        return instance;
    }

    /**
     * Called once by the loader's mod class while the game starts.
     *
     * @param configDir the game's config folder; the mod keeps its files in {@code petthoughts/} inside it
     */
    public PetThoughtsMod(Path configDir) {
        instance = this;
        files = new DataFiles(configDir.resolve(MOD_ID), PetThoughtsMod.class.getClassLoader(), LOGGER::warn);
        director = new ThoughtDirector(this);
        events = new PetEvents(this, director);
        bubbles = new ModBubbles(this);
        commands = new Commands(this, director);
    }

    // ------------------------------------------------------------------
    //  Called by the loader
    // ------------------------------------------------------------------

    public void serverStarted(MinecraftServer started) {
        server = started;
    }

    public void serverStopping() {
        bubbles.clear();
        delayed.clear();
        director.reset();
        hiddenPlayers.clear();
    }

    public void serverStopped() {
        server = null;
    }

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        ModCommand.register(dispatcher, this, commands);
    }

    // ------------------------------------------------------------------
    //  Ticking: delayed reactions, bubbles and the pet scan
    // ------------------------------------------------------------------

    /** Once per server tick, after the game has ticked. */
    public void tick(MinecraftServer running) {
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
        if (!player.isAlive() || player.isSpectator() || settings().worldDisabled(ModPet.worldId(player.level()))) {
            return;
        }
        OwnerView owner = ModPet.owner(player);
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
        bubbles.show(((ModPet) pet).entity(), pet.species().rideable(), pet.species().bubbleGap(), owner.id(),
                bubble, ModText.bubble(bubble));
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
        delayed.add(new Delayed(((ModPet) pet).entity(), tick + Math.max(1, ticks), action));
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
        for (Entity entity : level.getEntities((Entity) null, box, candidate -> ModPet.speciesOf(candidate) != null)) {
            ModPet pet = ModPet.of(this, entity);
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

    ModBubbles bubbles() {
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
