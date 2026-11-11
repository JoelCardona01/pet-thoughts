package dev.petthoughts.fabric;

import com.mojang.math.Transformation;
import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.bubble.Bubble;
import dev.petthoughts.core.config.Settings;
import dev.petthoughts.fabric.mixin.DisplayInvoker;
import dev.petthoughts.fabric.mixin.TextDisplayInvoker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a designed {@link Bubble} with vanilla Text Displays (same approach as the plugin): the
 * text display is the root, every rectangle of the frame and tail rides it. Wolves, cats and
 * parrots carry the root as a passenger; rideable species get it moved every tick so it never
 * blocks mounting. Everything runs on the server thread.
 */
final class FabricBubbles {

    /** Scoreboard tag on every piece: leftovers are recognised and removed when a world loads. */
    static final String TAG = "petthoughts_bubble";
    private static final float PASSENGER_LIFT = 0.12f;

    private record Piece(Display.TextDisplay display, Bubble.Rect rect) {}

    private static final class Active {
        final Bubble design;
        final Display.TextDisplay root;
        final List<Piece> pieces;
        final LivingEntity pet;
        final UUID owner;
        final boolean follow;
        final float size;
        final float lift;
        int age;
        int nextFrame = 1;

        Active(Bubble design, Display.TextDisplay root, List<Piece> pieces, LivingEntity pet, UUID owner,
                boolean follow, float size, float lift) {
            this.design = design;
            this.root = root;
            this.pieces = pieces;
            this.pet = pet;
            this.owner = owner;
            this.follow = follow;
            this.size = size;
            this.lift = lift;
        }

        boolean contains(Entity entity) {
            if (root == entity) {
                return true;
            }
            for (Piece piece : pieces) {
                if (piece.display == entity) {
                    return true;
                }
            }
            return false;
        }

        void discard() {
            for (Piece piece : pieces) {
                piece.display.discard();
            }
            root.discard();
        }
    }

    private final PetThoughtsMod mod;
    private final Map<UUID, Active> active = new HashMap<>();

    FabricBubbles(PetThoughtsMod mod) {
        this.mod = mod;
    }

    Trigger.Tier tierOf(UUID pet) {
        Active bubble = active.get(pet);
        return bubble == null ? null : bubble.design.tier();
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

    /** True if the entity is a piece of a bubble currently being shown (as opposed to a leftover). */
    boolean owns(Entity entity) {
        for (Active bubble : active.values()) {
            if (bubble.contains(entity)) {
                return true;
            }
        }
        return false;
    }

    /** True if this entity is a bubble piece that the given player must not see (owner-only mode, /pt toggle). */
    boolean hiddenFrom(Entity entity, ServerPlayer viewer) {
        if (!(entity instanceof Display.TextDisplay) || !entity.entityTags().contains(TAG)) {
            return false;
        }
        if (mod.isHidden(viewer.getUUID())) {
            return true;
        }
        if (!mod.settings().ownerOnly) {
            return false;
        }
        for (Active bubble : active.values()) {
            if (bubble.contains(entity)) {
                return !bubble.owner.equals(viewer.getUUID());
            }
        }
        return true;
    }

    void show(LivingEntity pet, boolean follow, float gap, UUID owner, Bubble bubble, Component text) {
        if (!(pet.level() instanceof ServerLevel level)) {
            return;
        }
        Settings settings = mod.settings();
        remove(pet.getUUID());

        float size = settings.scale * (pet.isBaby() ? 0.8f : 1.0f);
        float lift = follow ? pet.getBbHeight() + gap : PASSENGER_LIFT;
        Bubble.Keyframe first = bubble.keyframes().get(0);

        Display.TextDisplay root = create(level, pet, settings);
        if (root == null) {
            return;
        }
        ((TextDisplayInvoker) root).petthoughts$setText(text);
        ((TextDisplayInvoker) root).petthoughts$setLineWidth(10_000); // already wrapped by the designer
        ((TextDisplayInvoker) root).petthoughts$setBackgroundColor(0);
        apply(root, bubble.textTransform(first, size, lift), 0);
        if (follow) {
            ((DisplayInvoker) root).petthoughts$setTeleportDuration(2);
        }

        List<Piece> pieces = new ArrayList<>(bubble.rects().size());
        for (Bubble.Rect rect : bubble.rects()) {
            Display.TextDisplay piece = create(level, pet, settings);
            if (piece == null) {
                continue;
            }
            ((TextDisplayInvoker) piece).petthoughts$setText(Component.literal(" "));
            ((TextDisplayInvoker) piece).petthoughts$setBackgroundColor(rect.argb());
            apply(piece, Bubble.rectTransform(rect, first, size, lift), 0);
            pieces.add(new Piece(piece, rect));
        }

        // Registered before spawning so the visibility check already knows who owns each piece.
        Active shown = new Active(bubble, root, pieces, pet, owner, follow, size, lift);
        active.put(pet.getUUID(), shown);
        boolean ok = level.addFreshEntity(root) && (follow || root.startRiding(pet, true, false));
        for (Piece piece : pieces) {
            ok &= level.addFreshEntity(piece.display) && piece.display.startRiding(root, true, false);
        }
        if (!ok) {
            active.remove(pet.getUUID());
            shown.discard();
        }
    }

    private static Display.TextDisplay create(ServerLevel level, LivingEntity pet, Settings settings) {
        Display.TextDisplay display = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            return null;
        }
        display.addTag(TAG);
        display.setPermanentlyInvulnerable(true);
        display.snapTo(pet.getX(), pet.getY(), pet.getZ(), 0f, 0f);
        ((TextDisplayInvoker) display).petthoughts$setFlags(settings.seeThrough ? Display.TextDisplay.FLAG_SEE_THROUGH : 0);
        DisplayInvoker base = (DisplayInvoker) display;
        base.petthoughts$setBillboard(Display.BillboardConstraints.CENTER);
        base.petthoughts$setBrightness(Brightness.FULL_BRIGHT);
        base.petthoughts$setViewRange(settings.viewRange);
        return display;
    }

    /** Called every server tick: keyframes, following, removal. */
    void tick() {
        if (active.isEmpty()) {
            return;
        }
        for (Iterator<Active> it = active.values().iterator(); it.hasNext(); ) {
            Active bubble = it.next();
            bubble.age++;
            boolean gone = bubble.root.isRemoved() || !bubble.pet.isAlive() || bubble.pet.isRemoved()
                    || bubble.pet.level() != bubble.root.level();
            if (gone || bubble.age >= bubble.design.lifetime()) {
                it.remove();
                bubble.discard();
                continue;
            }
            List<Bubble.Keyframe> frames = bubble.design.keyframes();
            while (bubble.nextFrame < frames.size() && frames.get(bubble.nextFrame).tick() <= bubble.age) {
                Bubble.Keyframe frame = frames.get(bubble.nextFrame++);
                apply(bubble.root, bubble.design.textTransform(frame, bubble.size, bubble.lift), frame.duration());
                for (Piece piece : bubble.pieces) {
                    apply(piece.display, Bubble.rectTransform(piece.rect, frame, bubble.size, bubble.lift), frame.duration());
                }
            }
            if (bubble.follow) {
                bubble.root.snapTo(bubble.pet.getX(), bubble.pet.getY(), bubble.pet.getZ(), 0f, 0f);
            }
        }
    }

    private static void apply(Display display, float[] t, int duration) {
        DisplayInvoker invoker = (DisplayInvoker) display;
        invoker.petthoughts$setInterpolationDelay(0);
        invoker.petthoughts$setInterpolationDuration(duration);
        invoker.petthoughts$setTransformation(new Transformation(
                new Vector3f(t[0], t[1], t[2]), new Quaternionf(), new Vector3f(t[3], t[4], 1f), new Quaternionf()));
    }

    void remove(UUID pet) {
        Active bubble = active.remove(pet);
        if (bubble != null) {
            bubble.discard();
        }
    }

    void clear() {
        for (Active bubble : new ArrayList<>(active.values())) {
            bubble.discard();
        }
        active.clear();
    }
}
