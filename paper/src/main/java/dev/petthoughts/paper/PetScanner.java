package dev.petthoughts.paper;

import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.thought.ThoughtDirector.Friend;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * One repeating task per online player (on that player's scheduler, so it is Folia-safe) that
 * looks for the player's own pets nearby. The world is never iterated: no players, no work.
 */
final class PetScanner {

    private static final long PERIOD_TICKS = 40;
    private static final double RADIUS = 32;

    private final PetThoughtsPlugin plugin;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    PetScanner(PetThoughtsPlugin plugin) {
        this.plugin = plugin;
    }

    void startAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            start(player);
        }
    }

    void start(Player player) {
        stop(player.getUniqueId());
        // Random first delay so players who joined together are not all scanned on the same tick.
        long delay = 20 + ThreadLocalRandom.current().nextInt((int) PERIOD_TICKS);
        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin, t -> scan(player), () -> tasks.remove(player.getUniqueId()), delay, PERIOD_TICKS);
        if (task != null) {
            tasks.put(player.getUniqueId(), task);
        }
    }

    /** Restarts the player's task if it is gone (e.g. retired after a respawn). */
    void ensure(Player player) {
        ScheduledTask task = tasks.get(player.getUniqueId());
        if (task == null || task.isCancelled()) {
            start(player);
        }
    }

    void stop(UUID player) {
        ScheduledTask task = tasks.remove(player);
        if (task != null) {
            task.cancel();
        }
    }

    void stopAll() {
        for (UUID id : new ArrayList<>(tasks.keySet())) {
            stop(id);
        }
    }

    private void scan(Player player) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || plugin.settings().worldDisabled(player.getWorld().getName())) {
            return;
        }
        UUID id = player.getUniqueId();
        List<PaperPet> pets = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            PaperPet pet = PaperPet.of(plugin, entity);
            if (pet != null && id.equals(pet.ownerId()) && plugin.settings().speciesEnabled(pet.species())) {
                pets.add(pet);
            }
        }
        if (pets.isEmpty()) {
            return;
        }
        OwnerView owner = PaperPet.owner(player);
        List<Friend> friends = new ArrayList<>(pets.size());
        for (PaperPet pet : pets) {
            friends.add(Friend.of(pet));
        }
        for (PaperPet pet : pets) {
            // Each pet is evaluated on its own scheduler: the right thread on Folia, next tick on Paper.
            pet.entity().getScheduler().run(plugin, t -> plugin.director().evaluate(pet, owner, friends), null);
        }
    }
}
