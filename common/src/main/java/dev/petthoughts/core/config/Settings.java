package dev.petthoughts.core.config;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Species;
import dev.petthoughts.core.Trigger;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Immutable snapshot of config.yml. A reload simply builds a new one. */
public final class Settings {

    public final String language;
    public final boolean debug;
    public final boolean ownerOnly;
    public final double frequency;
    public final int displayTicks;
    public final long minIntervalMs;
    public final long maxIntervalMs;
    public final double idleChance;
    public final int maxBubblesPerPlayer;
    public final long ownerMinGapMs;
    public final int maxSameReaction;
    public final long urgentCooldownMs;
    public final long reactionGapMs;
    public final double ownerRange;
    public final double speciesLineChance;
    public final boolean pettingEnabled;
    public final boolean pettingHearts;
    public final boolean pettingSwapKey;
    public final boolean pettingSneakClick;
    public final boolean tail;
    public final boolean animation;
    public final boolean seeThrough;
    public final int lineWidth;
    public final float scale;
    /** Text Display view range in the game's unit (1.0 = 64 blocks). */
    public final float viewRange;

    /** Colours of one kind of bubble: opaque ARGB fill and outline, RGB text. */
    public record Palette(int fill, int outline, int text) {}

    private static final Map<Trigger.Category, String[]> DEFAULT_COLORS = Map.of(
            Trigger.Category.THOUGHT, new String[] {"#FFFFFF", "#3B3347", "#2B2533"},
            Trigger.Category.LOVE, new String[] {"#FFF0F4", "#B53A63", "#3D2030"},
            Trigger.Category.FOOD, new String[] {"#FFF6E2", "#9C5D2C", "#3B2617"},
            Trigger.Category.DANGER, new String[] {"#FFEDEA", "#C0392B", "#3D1512"});

    private final Map<Trigger.Category, Palette> palettes = new EnumMap<>(Trigger.Category.class);
    private final Set<Species> species = EnumSet.noneOf(Species.class);
    private final Map<Personality, Integer> weights = new EnumMap<>(Personality.class);
    private final Set<Trigger> disabledTriggers = EnumSet.noneOf(Trigger.class);
    private final Set<String> disabledWorlds = new HashSet<>();

    public Settings(YamlTree c, Consumer<String> warn) {
        language = c.string("language", "en").toLowerCase(Locale.ROOT);
        debug = c.bool("debug", false);
        ownerOnly = "owner".equalsIgnoreCase(c.string("visibility", "all"));
        frequency = clamp(c.number("frequency", 1.0), 0.05, 10.0);
        displayTicks = (int) Math.round(clamp(c.number("display-seconds", 4), 1, 30) * 20);
        long min = (long) (clamp(c.number("min-interval", 30), 1, 3600) * 1000);
        long max = (long) (clamp(c.number("max-interval", 75), 1, 3600) * 1000);
        minIntervalMs = Math.min(min, max);
        maxIntervalMs = Math.max(min, max);
        idleChance = clamp(c.number("idle-chance", 0.12), 0, 1);
        maxBubblesPerPlayer = (int) Math.max(1, c.number("max-bubbles-per-player", 3));
        ownerMinGapMs = (long) (clamp(c.number("owner-min-gap", 4), 0, 600) * 1000);
        maxSameReaction = (int) Math.max(1, c.number("max-same-reaction", 2));
        urgentCooldownMs = (long) (clamp(c.number("urgent-cooldown", 15), 2, 3600) * 1000);
        reactionGapMs = (long) (clamp(c.number("reaction-gap", 5), 0, 600) * 1000);
        ownerRange = clamp(c.number("owner-range", 24), 4, 32);
        speciesLineChance = clamp(c.number("species-line-chance", 0.35), 0, 1);
        pettingEnabled = c.bool("petting.enabled", true);
        pettingHearts = c.bool("petting.hearts", true);
        pettingSwapKey = pettingEnabled && c.bool("petting.swap-key", true);
        pettingSneakClick = pettingEnabled && c.bool("petting.sneak-click", true);

        for (Trigger.Category category : Trigger.Category.values()) {
            String[] d = DEFAULT_COLORS.get(category);
            String base = "bubble-style.colors." + category.id() + ".";
            palettes.put(category, new Palette(
                    0xFF000000 | rgb(c.string(base + "fill", d[0]), d[0], base + "fill", warn),
                    0xFF000000 | rgb(c.string(base + "outline", d[1]), d[1], base + "outline", warn),
                    rgb(c.string(base + "text", d[2]), d[2], base + "text", warn)));
        }
        tail = c.bool("bubble-style.tail", true);
        animation = c.bool("bubble-style.animation", true);
        seeThrough = c.bool("bubble-style.see-through", false);
        lineWidth = (int) Math.max(40, c.number("bubble-style.line-width", 150));
        scale = (float) clamp(c.number("bubble-style.scale", 1.0), 0.2, 4.0);
        viewRange = (float) (clamp(c.number("bubble-style.view-range", 32), 4, 128) / 64.0);

        for (Species s : Species.values()) {
            if (c.bool("species." + s.id(), true)) {
                species.add(s);
            }
        }
        for (Personality p : Personality.values()) {
            weights.put(p, (int) Math.max(0, c.number("personality-weights." + p.id(), 20)));
        }
        if (weights.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            warn.accept("All personality-weights are 0; every personality gets the same chance.");
            weights.replaceAll((p, w) -> 1);
        }
        for (String id : c.strings("disabled-triggers")) {
            Trigger t = Trigger.byId(id.trim());
            if (t == null) {
                warn.accept("Unknown trigger in disabled-triggers: " + id);
            } else {
                disabledTriggers.add(t);
            }
        }
        disabledWorlds.addAll(c.strings("disabled-worlds"));
    }

    public Palette palette(Trigger.Category category) {
        return palettes.get(category);
    }

    public boolean speciesEnabled(Species s) {
        return species.contains(s);
    }

    public boolean triggerDisabled(Trigger t) {
        return disabledTriggers.contains(t) || disabledTriggers.contains(t.group());
    }

    /** Matches a world by its plain name ("world_nether") or its key ("minecraft:the_nether"). */
    public boolean worldDisabled(String worldId) {
        return disabledWorlds.contains(worldId);
    }

    /** Weighted random personality for a newly seen pet. */
    public Personality randomPersonality() {
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<Personality, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) {
                return e.getKey();
            }
        }
        return Personality.LOYAL;
    }

    /** Random quiet time after a bubble, scaled by the frequency multiplier. */
    public long randomIntervalMs() {
        long base = minIntervalMs == maxIntervalMs
                ? minIntervalMs
                : ThreadLocalRandom.current().nextLong(minIntervalMs, maxIntervalMs + 1);
        return (long) (base / frequency);
    }

    /** "#RRGGBB" (an alpha part is ignored: bubbles are always opaque). */
    private static int rgb(String raw, String fallback, String key, Consumer<String> warn) {
        try {
            String hex = raw.trim().replace("#", "");
            if (hex.length() == 8) {
                hex = hex.substring(2);
            }
            if (hex.length() == 6) {
                return Integer.parseInt(hex, 16);
            }
        } catch (RuntimeException e) {
            // fall through to the warning
        }
        warn.accept("Invalid color for " + key + ": '" + raw + "'. Using " + fallback + ".");
        return Integer.parseInt(fallback.substring(1), 16);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
