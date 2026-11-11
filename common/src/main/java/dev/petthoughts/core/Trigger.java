package dev.petthoughts.core;

import java.util.List;
import java.util.Locale;

/**
 * Everything a pet can react to.
 *
 * <p>State triggers are polled by the scanner in the order of {@link #STATE_ORDER} (danger first,
 * random chatter last). Event triggers are pushed by listeners the moment something happens.
 *
 * <p>Columns: tier (how it interacts with the anti-spam rules), category (bubble prefix),
 * cooldown in seconds for one pet (-1 = the configurable urgent cooldown), chance that an event
 * produces a thought at all, and whether the trigger is "shared" (several pets of one owner notice
 * the same thing, so only a few of them should comment on it).
 */
public enum Trigger {
    // ---- State triggers (polled) ----
    PET_ON_FIRE(Tier.URGENT, Category.DANGER, -1, 1.0, false),
    PET_LOW_HEALTH(Tier.URGENT, Category.DANGER, 30, 1.0, false),
    OWNER_LOW_HEALTH(Tier.URGENT, Category.DANGER, 30, 1.0, true),
    HOSTILE_NEARBY(Tier.URGENT, Category.DANGER, -1, 1.0, true),
    HOSTILE_CREEPER(Tier.URGENT, Category.DANGER, -1, 1.0, true),
    OWNER_RETURNED(Tier.REACTION, Category.LOVE, 60, 1.0, false),
    IN_NETHER(Tier.AMBIENT, Category.THOUGHT, 300, 1.0, true),
    IN_END(Tier.AMBIENT, Category.THOUGHT, 300, 1.0, true),
    IN_WATER(Tier.AMBIENT, Category.THOUGHT, 120, 1.0, true),
    OWNER_HOLDS_FOOD(Tier.REACTION, Category.FOOD, 60, 1.0, true),
    OWNER_HUNGRY(Tier.AMBIENT, Category.FOOD, 180, 1.0, true),
    SITTING_LONG(Tier.AMBIENT, Category.THOUGHT, 600, 1.0, false),
    OWNER_SLEEPING(Tier.AMBIENT, Category.THOUGHT, 120, 1.0, true),
    IN_BOAT(Tier.AMBIENT, Category.THOUGHT, 180, 1.0, false),
    LEASHED(Tier.AMBIENT, Category.THOUGHT, 180, 1.0, false),
    THUNDER(Tier.AMBIENT, Category.DANGER, 240, 1.0, true),
    RAIN(Tier.AMBIENT, Category.THOUGHT, 300, 1.0, true),
    OWNER_FLYING(Tier.AMBIENT, Category.THOUGHT, 120, 1.0, true),
    NIGHT(Tier.AMBIENT, Category.THOUGHT, 600, 1.0, true),
    DAWN(Tier.AMBIENT, Category.THOUGHT, 600, 1.0, true),
    RIDDEN(Tier.AMBIENT, Category.THOUGHT, 90, 1.0, false),
    HIGH_ALTITUDE(Tier.AMBIENT, Category.THOUGHT, 300, 1.0, true),
    UNDERGROUND(Tier.AMBIENT, Category.THOUGHT, 300, 1.0, true),
    COLD_BIOME(Tier.AMBIENT, Category.THOUGHT, 600, 1.0, true),
    HOT_BIOME(Tier.AMBIENT, Category.THOUGHT, 600, 1.0, true),
    FRIEND_NEARBY(Tier.AMBIENT, Category.LOVE, 240, 0.25, false),
    IDLE(Tier.AMBIENT, Category.THOUGHT, 0, 1.0, false),

    // ---- Event triggers (pushed by listeners) ----
    TAMED(Tier.REACTION, Category.LOVE, 0, 1.0, false),
    FED(Tier.REACTION, Category.FOOD, 8, 1.0, false),
    PETTED(Tier.REACTION, Category.LOVE, 12, 1.0, false),
    PET_HURT(Tier.REACTION, Category.DANGER, 20, 0.7, false),
    HIT_BY_OWNER(Tier.REACTION, Category.DANGER, 15, 1.0, false),
    PET_KILLED_MOB(Tier.REACTION, Category.THOUGHT, 20, 0.8, false),
    OWNER_KILLED_MOB(Tier.AMBIENT, Category.THOUGHT, 45, 0.4, true),
    OWNER_HURT(Tier.AMBIENT, Category.DANGER, 40, 0.5, true),
    OWNER_DIED(Tier.URGENT, Category.DANGER, 30, 1.0, true),
    OWNER_ATE(Tier.AMBIENT, Category.FOOD, 60, 0.6, true),
    OWNER_FISHED(Tier.AMBIENT, Category.FOOD, 40, 0.7, true),
    OWNER_FOUND_TREASURE(Tier.REACTION, Category.THOUGHT, 60, 1.0, true),
    OWNER_ADVANCEMENT(Tier.REACTION, Category.LOVE, 30, 1.0, true),
    SAT_DOWN(Tier.REACTION, Category.THOUGHT, 40, 0.6, false),
    STOOD_UP(Tier.REACTION, Category.THOUGHT, 40, 0.6, false),
    TELEPORTED(Tier.AMBIENT, Category.THOUGHT, 60, 0.5, true),
    HAD_BABY(Tier.REACTION, Category.LOVE, 0, 1.0, false),
    MUSIC(Tier.AMBIENT, Category.THOUGHT, 60, 0.8, true),
    EXPLOSION(Tier.URGENT, Category.DANGER, -1, 1.0, true);

    /** How a trigger interacts with the anti-spam rules. */
    public enum Tier {
        /** Danger and health: ignores the global interval, may replace an ambient bubble. */
        URGENT,
        /** Direct answer to something the owner did: ignores the global interval, keeps a short gap. */
        REACTION,
        /** Everything else: respects the global interval and the per-player pace. */
        AMBIENT
    }

    /** Decides the bubble prefix. */
    public enum Category {
        DANGER,
        LOVE,
        FOOD,
        THOUGHT;

        private final String id = name().toLowerCase(Locale.ROOT);

        public String id() {
            return id;
        }
    }

    /** Polling order: first match that is not on cooldown wins. */
    public static final List<Trigger> STATE_ORDER = List.of(
            PET_ON_FIRE, PET_LOW_HEALTH, OWNER_LOW_HEALTH, HOSTILE_NEARBY, OWNER_RETURNED,
            IN_NETHER, IN_END, IN_WATER, OWNER_HOLDS_FOOD, OWNER_HUNGRY, SITTING_LONG, OWNER_SLEEPING,
            IN_BOAT, LEASHED, THUNDER, RAIN, OWNER_FLYING, NIGHT, DAWN, RIDDEN, HIGH_ALTITUDE,
            UNDERGROUND, COLD_BIOME, HOT_BIOME, FRIEND_NEARBY, IDLE);

    private final String id = name().toLowerCase(Locale.ROOT);
    private final Tier tier;
    private final Category category;
    private final int cooldownSeconds;
    private final double chance;
    private final boolean shared;

    Trigger(Tier tier, Category category, int cooldownSeconds, double chance, boolean shared) {
        this.tier = tier;
        this.category = category;
        this.cooldownSeconds = cooldownSeconds;
        this.chance = chance;
        this.shared = shared;
    }

    public String id() {
        return id;
    }

    public Tier tier() {
        return tier;
    }

    public Category category() {
        return category;
    }

    /** Cooldown for one pet in seconds; -1 means "use urgent-cooldown from the config". */
    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public double chance() {
        return chance;
    }

    public boolean shared() {
        return shared;
    }

    /** Variants share cooldowns and group limits with their base trigger. */
    public Trigger group() {
        return this == HOSTILE_CREEPER ? HOSTILE_NEARBY : this;
    }

    /** Trigger whose lines are used when this one has none in the lang file. */
    public Trigger fallback() {
        return this == HOSTILE_CREEPER ? HOSTILE_NEARBY : null;
    }

    public static Trigger byId(String id) {
        if (id == null) {
            return null;
        }
        for (Trigger t : values()) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }
}
