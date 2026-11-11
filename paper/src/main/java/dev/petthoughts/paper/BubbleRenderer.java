package dev.petthoughts.paper;

import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.bubble.Bubble;
import dev.petthoughts.core.config.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Draws a designed {@link Bubble} with vanilla Text Displays: one for the text and one per
 * rectangle of the frame and tail, all billboarded around the same point.
 *
 * <p>The text display is the root: wolves, cats and parrots carry it as a passenger, rideable
 * species get it teleported every tick (an extra passenger would stop the owner from mounting).
 * Every other piece rides the root, so the whole bubble moves as one.
 */
final class BubbleRenderer {

    /** Height of the tail tip above the pet's back (passenger) or above its hitbox (follow). */
    private static final float PASSENGER_LIFT = 0.12f;

    private record Piece(TextDisplay display, Bubble.Rect rect) {}

    private record Active(TextDisplay root, List<Piece> pieces, UUID owner, Trigger.Tier tier) {
        void forEach(java.util.function.Consumer<TextDisplay> action) {
            action.accept(root);
            for (Piece piece : pieces) {
                action.accept(piece.display);
            }
        }
    }

    private final PetThoughtsPlugin plugin;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    BubbleRenderer(PetThoughtsPlugin plugin) {
        this.plugin = plugin;
    }

    Trigger.Tier tierOf(UUID pet) {
        Active bubble = active.get(pet);
        return bubble == null ? null : bubble.tier;
    }

    int countFor(UUID owner) {
        int count = 0;
        for (Active bubble : active.values()) {
            if (bubble.owner.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** Shows a bubble over the pet, replacing the current one if there is any. Must run on the pet's thread. */
    void show(LivingEntity pet, boolean follow, float gap, UUID ownerId, Bubble bubble, Component text) {
        Settings settings = plugin.settings();
        remove(pet.getUniqueId());

        float size = settings.scale * (pet instanceof Ageable ageable && !ageable.isAdult() ? 0.8f : 1.0f);
        float lift = follow ? (float) pet.getHeight() + gap : PASSENGER_LIFT;
        Bubble.Keyframe first = bubble.keyframes().get(0);
        Location at = pet.getLocation();
        at.setYaw(0);
        at.setPitch(0);

        TextDisplay root = pet.getWorld().spawn(at, TextDisplay.class, d -> {
            setUp(d, settings);
            d.text(text);
            d.setLineWidth(10_000); // already wrapped by the designer
            d.setBackgroundColor(Color.fromARGB(0));
            d.setTransformation(transformation(bubble.textTransform(first, size, lift)));
            if (follow) {
                d.setTeleportDuration(2);
            }
        });
        List<Piece> pieces = new ArrayList<>(bubble.rects().size());
        for (Bubble.Rect rect : bubble.rects()) {
            TextDisplay piece = pet.getWorld().spawn(at, TextDisplay.class, d -> {
                setUp(d, settings);
                d.text(Component.text(" "));
                d.setBackgroundColor(Color.fromARGB(rect.argb()));
                d.setTransformation(transformation(Bubble.rectTransform(rect, first, size, lift)));
            });
            root.addPassenger(piece);
            pieces.add(new Piece(piece, rect));
        }

        if (!follow && !pet.addPassenger(root)) {
            root.remove();
            pieces.forEach(p -> p.display.remove());
            return;
        }
        UUID petId = pet.getUniqueId();
        Active shown = new Active(root, pieces, ownerId, bubble.tier());
        active.put(petId, shown);
        applyVisibility(shown, ownerId);

        Runnable gone = () -> discard(petId, shown);
        List<Bubble.Keyframe> frames = bubble.keyframes();
        for (int i = 1; i < frames.size(); i++) {
            Bubble.Keyframe frame = frames.get(i);
            pet.getScheduler().runDelayed(plugin, task -> animate(shown, bubble, frame, size, lift), gone,
                    Math.max(1, frame.tick()));
        }
        pet.getScheduler().runDelayed(plugin, task -> discard(petId, shown), gone, bubble.lifetime());
        if (follow) {
            pet.getScheduler().runAtFixedRate(plugin, task -> {
                if (!root.isValid() || !pet.isValid()) {
                    task.cancel();
                    return;
                }
                Location target = pet.getLocation();
                target.setYaw(0);
                target.setPitch(0);
                root.teleportAsync(target);
            }, gone, 1, 1);
        }
    }

    private void setUp(TextDisplay d, Settings settings) {
        d.setPersistent(false);
        d.getPersistentDataContainer().set(plugin.keys().bubble, PersistentDataType.BYTE, (byte) 1);
        d.setBillboard(Display.Billboard.CENTER);
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setDefaultBackground(false);
        d.setShadowed(false);
        d.setSeeThrough(settings.seeThrough);
        d.setViewRange(settings.viewRange);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setInvulnerable(true);
        if (settings.ownerOnly) {
            d.setVisibleByDefault(false);
        }
    }

    private void applyVisibility(Active bubble, UUID ownerId) {
        if (plugin.settings().ownerOnly) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && !plugin.isHidden(ownerId)) {
                owner.getScheduler().run(plugin, task -> bubble.forEach(d -> owner.showEntity(plugin, d)), null);
            }
            return;
        }
        // Players who ran /petthoughts toggle never see bubbles.
        for (UUID id : plugin.hiddenPlayers()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) {
                viewer.getScheduler().run(plugin, task -> bubble.forEach(d -> viewer.hideEntity(plugin, d)), null);
            }
        }
    }

    private static void animate(Active bubble, Bubble design, Bubble.Keyframe frame, float size, float lift) {
        if (!bubble.root.isValid()) {
            return;
        }
        apply(bubble.root, design.textTransform(frame, size, lift), frame.duration());
        for (Piece piece : bubble.pieces) {
            if (piece.display.isValid()) {
                apply(piece.display, Bubble.rectTransform(piece.rect, frame, size, lift), frame.duration());
            }
        }
    }

    private static void apply(TextDisplay display, float[] t, int duration) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(transformation(t));
    }

    private static Transformation transformation(float[] t) {
        return new Transformation(new Vector3f(t[0], t[1], t[2]), new AxisAngle4f(),
                new Vector3f(t[3], t[4], 1f), new AxisAngle4f());
    }

    /** Removes the pet's bubble right now (pet died, bubble replaced...). */
    void remove(UUID pet) {
        Active bubble = active.remove(pet);
        if (bubble != null) {
            bubble.forEach(this::removeEntity);
        }
    }

    private void discard(UUID pet, Active bubble) {
        active.remove(pet, bubble);
        bubble.forEach(this::removeEntity);
    }

    private void removeEntity(Entity entity) {
        try {
            if (Bukkit.isOwnedByCurrentRegion(entity)) {
                entity.remove();
            } else {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
            }
        } catch (RuntimeException e) {
            // Plugin disabling or entity already gone: it is not persistent, so it cannot be saved anyway.
        }
    }

    /** Removes every bubble (reload / shutdown). */
    void clear() {
        for (UUID pet : active.keySet()) {
            Active bubble = active.remove(pet);
            if (bubble != null) {
                bubble.forEach(d -> {
                    try {
                        d.remove();
                    } catch (RuntimeException e) {
                        removeEntity(d);
                    }
                });
            }
        }
    }
}
