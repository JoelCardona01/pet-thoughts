package dev.petthoughts.paper;

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
import dev.petthoughts.core.bubble.Bubble;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** The Paper/Folia plugin: wires Bukkit to the shared brain in the common module. */
public final class PetThoughtsPlugin extends JavaPlugin implements Platform {

    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private PaperPet.Keys keys;
    private DataFiles files;
    private ThoughtDirector director;
    private PetEvents events;
    private BubbleRenderer bubbles;
    private PetScanner scanner;
    private ScheduledTask purgeTask;

    @Override
    public void onEnable() {
        keys = new PaperPet.Keys(this);
        files = new DataFiles(getDataFolder().toPath(), getClassLoader(), message -> getLogger().warning(message));
        director = new ThoughtDirector(this);
        events = new PetEvents(this, director);
        bubbles = new BubbleRenderer(this);
        scanner = new PetScanner(this);

        getServer().getPluginManager().registerEvents(new PetListener(this), this);
        PaperCommand command = new PaperCommand(this, new Commands(this, director));
        PluginCommand registered = getCommand("petthoughts");
        if (registered != null) {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }

        removeOrphanBubbles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            setHidden(player.getUniqueId(), player.getPersistentDataContainer().has(keys.hidden, PersistentDataType.BYTE));
        }
        scanner.startAll();
        purgeTask = Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> director.purge(), 10, 10, TimeUnit.MINUTES);
    }

    @Override
    public void onDisable() {
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        if (scanner != null) {
            scanner.stopAll();
        }
        if (bubbles != null) {
            bubbles.clear();
        }
    }

    /** Bubbles left behind by a crash or a hard reload. Loaded chunks only; the rest is handled on chunk load. */
    private void removeOrphanBubbles() {
        try {
            int removed = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                    if (entity.getPersistentDataContainer().has(keys.bubble, PersistentDataType.BYTE)) {
                        entity.remove();
                        removed++;
                    }
                }
            }
            if (removed > 0) {
                getLogger().info("Removed " + removed + " leftover thought bubble(s).");
            }
        } catch (RuntimeException | LinkageError e) {
            // Folia does not allow iterating world entities from here; EntitiesLoadEvent covers it.
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
        getLogger().info(message);
    }

    /** New settings and language, cooldowns reset, scan tasks restarted (never duplicated). */
    @Override
    public void reload() {
        files.reload();
        bubbles.clear();
        director.reset();
        scanner.stopAll();
        scanner.startAll();
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
        bubbles.show(((PaperPet) pet).entity(), pet.species().rideable(), pet.species().bubbleGap(), owner.id(),
                bubble, PaperText.bubble(bubble));
    }

    @Override
    public void removeBubble(UUID pet) {
        bubbles.remove(pet);
    }

    @Override
    public void later(PetView pet, int ticks, Runnable action) {
        ((PaperPet) pet).entity().getScheduler().runDelayed(this, task -> action.run(), null, Math.max(1, ticks));
    }

    @Override
    public List<PetView> petsNear(OwnerView owner, double x, double y, double z, double radius) {
        List<PetView> pets = new ArrayList<>();
        World world = Bukkit.getWorld(owner.worldId());
        if (world == null) {
            return pets;
        }
        for (Entity entity : world.getNearbyEntities(new Location(world, x, y, z), radius, radius, radius)) {
            PaperPet pet = PaperPet.of(this, entity);
            if (pet != null && owner.id().equals(pet.ownerId())) {
                pets.add(pet);
            }
        }
        return pets;
    }

    // ------------------------------------------------------------------

    PaperPet.Keys keys() {
        return keys;
    }

    ThoughtDirector director() {
        return director;
    }

    PetEvents events() {
        return events;
    }

    PetScanner scanner() {
        return scanner;
    }

    /** Players who turned bubbles off with /petthoughts toggle. */
    Set<UUID> hiddenPlayers() {
        return hiddenPlayers;
    }

    boolean isHidden(UUID player) {
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
