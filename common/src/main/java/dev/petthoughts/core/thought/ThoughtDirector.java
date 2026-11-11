package dev.petthoughts.core.thought;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Species;
import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.Trigger.Tier;
import dev.petthoughts.core.bubble.Bubble;
import dev.petthoughts.core.bubble.BubbleDesigner;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.config.Settings;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import dev.petthoughts.core.platform.PetView.Dimension;
import dev.petthoughts.core.platform.Platform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The brain: decides whether a pet thinks something right now.
 *
 * <p>Two entry points: {@link #evaluate} (polled state triggers, called by each platform's
 * scanner) and {@link #react} (event triggers, called through {@link PetEvents}). Both end in
 * {@link #tryThink}, the single place where every anti-spam rule is applied. Everything here runs
 * on the pet's own thread.
 */
public final class ThoughtDirector {

    /** A pet's owner was away at least this long before OWNER_RETURNED fires. */
    private static final long AWAY_MS = 10 * 60_000L;
    private static final long SITTING_LONG_MS = 10 * 60_000L;
    private static final long HAPPY_MS = 2 * 60_000L;
    private static final long STATE_TTL_MS = 30 * 60_000L;
    private static final double CLOSE_RANGE = 16.0;

    /** Another pet of the same owner seen in the same scan. */
    public record Friend(UUID id, String name, String worldId, double x, double y, double z) {
        public static Friend of(PetView pet) {
            return new Friend(pet.id(), pet.plainName(), pet.worldId(), pet.x(), pet.y(), pet.z());
        }
    }

    public enum Result { SHOWN, BLOCKED, CAPPED, NO_LINE }

    private enum Gate { OPEN, CLOSED, CAPPED }

    private record Match(Trigger trigger, ThoughtVars vars) {
        static Match of(Trigger trigger) {
            return new Match(trigger, ThoughtVars.NONE);
        }
    }

    private final Platform platform;
    private final Map<UUID, PetState> pets = new ConcurrentHashMap<>();
    private final Map<UUID, OwnerState> owners = new ConcurrentHashMap<>();

    public ThoughtDirector(Platform platform) {
        this.platform = platform;
    }

    // ------------------------------------------------------------------
    //  Polled state triggers
    // ------------------------------------------------------------------

    /** Periodic check of one pet. Called every scan (2 s) while its owner is nearby. */
    public void evaluate(PetView pet, OwnerView owner, List<Friend> friends) {
        Settings settings = platform.settings();
        if (!pet.alive() || !settings.speciesEnabled(pet.species()) || settings.worldDisabled(pet.worldId())) {
            return;
        }
        long now = System.currentTimeMillis();
        ensurePersonality(pet);
        PetState state = state(pet.id(), now);
        double distance = owner.distanceTo(pet);

        Trigger sitChange = updateMemory(pet, state, distance, now);

        if (pet.muted() || distance > settings.ownerRange || pet.invisible()) {
            return;
        }
        if (sitChange != null && distance <= 6 && roll(sitChange)
                && tryThink(pet, owner, sitChange, ThoughtVars.NONE, false) == Result.SHOWN) {
            return;
        }
        OwnerState ownerState = owners.computeIfAbsent(owner.id(), id -> new OwnerState());
        for (Trigger trigger : Trigger.STATE_ORDER) {
            Gate gate = gate(pet.id(), state, ownerState, owner.id(), trigger, now);
            if (gate == Gate.CAPPED) {
                return;
            }
            if (gate == Gate.CLOSED) {
                continue;
            }
            Match match = test(trigger, pet, owner, friends, state, distance, now);
            if (match == null) {
                continue;
            }
            Result result = tryThink(pet, owner, match.trigger, match.vars, false);
            if (result == Result.SHOWN || result == Result.CAPPED) {
                return;
            }
        }
    }

    /**
     * Keeps the "since when" facts up to date even while the pet is quiet. Returns SAT_DOWN or
     * STOOD_UP if the pet changed posture since the last check, otherwise null.
     */
    private Trigger updateMemory(PetView pet, PetState state, double distance, long now) {
        Dimension dimension = pet.dimension();
        if (state.dimension != dimension) {
            // New dimension: the "what is this place" thought is available again.
            state.dimension = dimension;
            state.clearCooldown(Trigger.IN_NETHER);
            state.clearCooldown(Trigger.IN_END);
        }
        if (!pet.storming()) {
            state.rainSaid = false;
            state.thunderSaid = false;
        } else if (!pet.thundering()) {
            state.thunderSaid = false;
        }
        if (distance <= CLOSE_RANGE) {
            long seen = pet.ownerSeen();
            if (seen > 0 && now - seen >= AWAY_MS) {
                state.returnedUntil = now + 20_000;
            }
            if (now - seen > 30_000) {
                pet.setOwnerSeen(now); // at most one write every 30 s
            }
        }
        Trigger change = null;
        Boolean sitting = pet.sitting();
        if (sitting != null) {
            long since = pet.sitSince();
            if (sitting && since == 0) {
                pet.setSitSince(now);
            } else if (!sitting && since != 0) {
                pet.setSitSince(0);
            }
            if (state.wasSitting != null && state.wasSitting != sitting) {
                change = sitting ? Trigger.SAT_DOWN : Trigger.STOOD_UP;
            }
            state.wasSitting = sitting;
        }
        return change;
    }

    /** Returns what to think if the trigger's condition holds right now, otherwise null. */
    private Match test(Trigger trigger, PetView pet, OwnerView owner, List<Friend> friends, PetState state,
            double distance, long now) {
        Settings settings = platform.settings();
        boolean overworld = pet.dimension() == Dimension.OVERWORLD;
        long time = pet.dayTime();
        return switch (trigger) {
            case PET_ON_FIRE -> pet.onFire() ? Match.of(trigger) : null;
            case PET_LOW_HEALTH -> pet.healthRatio() < 0.30 ? Match.of(trigger) : null;
            case OWNER_LOW_HEALTH -> distance <= CLOSE_RANGE && owner.healthRatio() > 0 && owner.healthRatio() < 0.25
                    ? Match.of(trigger) : null;
            case HOSTILE_NEARBY -> {
                PetView.Hostile hostile = pet.nearestHostile();
                if (hostile == null) {
                    yield null;
                }
                yield new Match(hostile.creeper() ? Trigger.HOSTILE_CREEPER : trigger, ThoughtVars.mob(hostile.name()));
            }
            case OWNER_RETURNED -> state.returnedUntil > now ? Match.of(trigger) : null;
            case IN_NETHER -> pet.dimension() == Dimension.NETHER ? Match.of(trigger) : null;
            case IN_END -> pet.dimension() == Dimension.END ? Match.of(trigger) : null;
            case IN_WATER -> pet.inWater() ? Match.of(trigger) : null;
            case OWNER_HOLDS_FOOD -> {
                if (distance > 6) {
                    yield null;
                }
                Species species = pet.species();
                String food = species.likes(owner.mainHand()) ? owner.mainHand()
                        : species.likes(owner.offHand()) ? owner.offHand() : null;
                yield food == null ? null : new Match(trigger, ThoughtVars.food(food));
            }
            case OWNER_HUNGRY -> distance <= CLOSE_RANGE && owner.foodLevel() <= 6 ? Match.of(trigger) : null;
            case SITTING_LONG -> {
                long since = pet.sitSince();
                yield since > 0 && now - since >= SITTING_LONG_MS ? Match.of(trigger) : null;
            }
            case OWNER_SLEEPING -> owner.sleeping() ? Match.of(trigger) : null;
            case IN_BOAT -> pet.inBoat() ? Match.of(trigger) : null;
            case LEASHED -> pet.leashed() ? Match.of(trigger) : null;
            case THUNDER -> overworld && pet.storming() && pet.thundering() && !state.thunderSaid
                    ? Match.of(trigger) : null;
            case RAIN -> !state.rainSaid && pet.inRain() ? Match.of(trigger) : null;
            case OWNER_FLYING -> owner.gliding() ? Match.of(trigger) : null;
            case NIGHT -> overworld && time >= 13000 && time < 23000 && state.nightSaidDay != day(pet)
                    ? Match.of(trigger) : null;
            case DAWN -> overworld && (time >= 23000 || time < 1000) && state.dawnSaidDay != dawnDay(pet)
                    ? Match.of(trigger) : null;
            case RIDDEN -> pet.riddenBy(owner.id()) ? Match.of(trigger) : null;
            case HIGH_ALTITUDE -> overworld && pet.y() > 200 ? Match.of(trigger) : null;
            case UNDERGROUND -> overworld && pet.y() < 40 && pet.skyLight() == 0 ? Match.of(trigger) : null;
            case COLD_BIOME -> overworld && pet.temperature() < 0.15 ? Match.of(trigger) : null;
            case HOT_BIOME -> overworld && pet.temperature() >= 1.5 ? Match.of(trigger) : null;
            case FRIEND_NEARBY -> {
                if (!roll(trigger)) {
                    yield null;
                }
                Friend nearest = null;
                double best = 5 * 5;
                for (Friend friend : friends) {
                    if (friend.id().equals(pet.id()) || !friend.worldId().equals(pet.worldId())) {
                        continue;
                    }
                    double dx = friend.x() - pet.x();
                    double dy = friend.y() - pet.y();
                    double dz = friend.z() - pet.z();
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d <= best) {
                        best = d;
                        nearest = friend;
                    }
                }
                yield nearest == null ? null : new Match(trigger, ThoughtVars.friend(nearest.name()));
            }
            case IDLE -> ThreadLocalRandom.current().nextDouble() < settings.idleChance * Math.min(1.0, settings.frequency)
                    ? Match.of(trigger) : null;
            default -> null;
        };
    }

    private static long day(PetView pet) {
        return (pet.gameTime() - pet.dayTime()) / 24000L;
    }

    /** The dawn window crosses midnight (23000-1000), so shift it to keep one id per dawn. */
    private static long dawnDay(PetView pet) {
        return (pet.gameTime() + 1000L) / 24000L;
    }

    static boolean roll(Trigger trigger) {
        return trigger.chance() >= 1.0 || ThreadLocalRandom.current().nextDouble() < trigger.chance();
    }

    // ------------------------------------------------------------------
    //  Event triggers
    // ------------------------------------------------------------------

    /** Something happened to (or around) this pet. Must run on the pet's thread. */
    public Result react(PetView pet, OwnerView owner, Trigger trigger, ThoughtVars vars) {
        Settings settings = platform.settings();
        if (!pet.alive() || !settings.speciesEnabled(pet.species()) || settings.worldDisabled(pet.worldId())
                || pet.muted() || pet.invisible() || owner.distanceTo(pet) > settings.ownerRange) {
            return Result.BLOCKED;
        }
        ensurePersonality(pet);
        if (trigger == Trigger.FED || trigger == Trigger.PETTED) {
            long now = System.currentTimeMillis();
            state(pet.id(), now).lastHappyAt = now;
        }
        return tryThink(pet, owner, trigger, vars, false);
    }

    /** /petthoughts test: shows a thought ignoring every cooldown and limit. */
    public Result force(PetView pet, OwnerView owner, Trigger trigger) {
        ensurePersonality(pet);
        ThoughtVars vars = new ThoughtVars(platform.lang().raw("generic.test-mob"), null, "cooked_beef");
        return tryThink(pet, owner, trigger, vars, true);
    }

    // ------------------------------------------------------------------
    //  Anti-spam
    // ------------------------------------------------------------------

    /** Cheap checks that do not need to look at the world: cooldowns, intervals and bubble limits. */
    private Gate gate(UUID petId, PetState state, OwnerState ownerState, UUID ownerId, Trigger trigger, long now) {
        Settings settings = platform.settings();
        if (settings.triggerDisabled(trigger) || state.onCooldown(trigger, now)) {
            return Gate.CLOSED;
        }
        Tier current = platform.bubbleTier(petId);
        boolean replacing = false;
        if (current != null) {
            // One bubble per pet. Only danger may interrupt, and only something that is not danger.
            if (trigger.tier() != Tier.URGENT || current == Tier.URGENT) {
                return Gate.CLOSED;
            }
            replacing = true;
        }
        switch (trigger.tier()) {
            case AMBIENT -> {
                if (now < state.nextAmbientAt || now - ownerState.lastBubbleAt() < settings.ownerMinGapMs) {
                    return Gate.CLOSED;
                }
            }
            case REACTION -> {
                if (now - state.lastBubbleAt < settings.reactionGapMs) {
                    return Gate.CLOSED;
                }
            }
            case URGENT -> {
                if (now - state.lastBubbleAt < 2_000) {
                    return Gate.CLOSED;
                }
            }
        }
        int visible = platform.bubbleCount(ownerId) - (replacing ? 1 : 0);
        return visible >= settings.maxBubblesPerPlayer ? Gate.CAPPED : Gate.OPEN;
    }

    private Result tryThink(PetView pet, OwnerView owner, Trigger trigger, ThoughtVars vars, boolean force) {
        Settings settings = platform.settings();
        long now = System.currentTimeMillis();
        PetState state = state(pet.id(), now);
        OwnerState ownerState = owners.computeIfAbsent(owner.id(), id -> new OwnerState());

        if (!force) {
            Gate gate = gate(pet.id(), state, ownerState, owner.id(), trigger, now);
            if (gate != Gate.OPEN) {
                return gate == Gate.CAPPED ? Result.CAPPED : Result.BLOCKED;
            }
            if (trigger.shared() && !ownerState.takeCrowdSlot(trigger, now, settings.maxSameReaction)) {
                // Enough pets already said this: this one stays quiet about it, as if it had spoken.
                consume(state, trigger, pet, now);
                return Result.BLOCKED;
            }
        }

        String line = pickLine(pet.personality(), pet.species(), trigger, state.lastLine(trigger), ownerState.recentLines());
        if (line == null) {
            return Result.NO_LINE;
        }
        Bubble bubble = BubbleDesigner.design(settings, platform.lang(), line, trigger, placeholders(pet, owner, vars));
        platform.showBubble(pet, owner, bubble);
        if (settings.debug) {
            platform.log("[thought] " + pet.plainName() + " (" + pet.species().id() + ", " + pet.personality().id() + ") of "
                    + owner.name() + " <" + trigger.id() + (force ? ", forced" : "") + "> " + bubble.plainText());
        }

        state.lastBubbleAt = now;
        state.nextAmbientAt = now + settings.randomIntervalMs();
        state.setLastLine(trigger, line);
        state.lastThought = line;
        ownerState.bubbleShown(now, line);
        if (!force) {
            consume(state, trigger, pet, now);
        }
        return Result.SHOWN;
    }

    /** Values for the variables a line can use, as plain text in the configured language. */
    private Map<String, Object> placeholders(PetView pet, OwnerView owner, ThoughtVars vars) {
        LangStore lang = platform.lang();
        Map<String, Object> values = new HashMap<>();
        values.put("owner", owner.name());
        values.put("pet", pet.plainName());
        values.put("mob", vars.mob() != null ? vars.mob() : lang.raw("generic.mob"));
        values.put("friend", vars.friend() != null ? vars.friend() : lang.raw("generic.friend"));
        values.put("food", Names.food(lang, vars.food()));
        return values;
    }

    /** Starts the trigger's cooldown and sets its "already said" flag. */
    private void consume(PetState state, Trigger trigger, PetView pet, long now) {
        Settings settings = platform.settings();
        long cooldown = trigger.cooldownSeconds() < 0 ? settings.urgentCooldownMs : trigger.cooldownSeconds() * 1000L;
        if (trigger.tier() == Tier.URGENT) {
            cooldown <<= state.bumpStreak(trigger, now);
        }
        state.setCooldown(trigger, now + cooldown);
        switch (trigger) {
            case RAIN -> state.rainSaid = true;
            case THUNDER -> {
                state.thunderSaid = true;
                state.rainSaid = true; // no "it's raining" right after "THUNDER!"
            }
            case NIGHT -> state.nightSaidDay = day(pet);
            case DAWN -> state.dawnSaidDay = dawnDay(pet);
            case OWNER_RETURNED -> state.returnedUntil = 0;
            default -> { }
        }
    }

    /**
     * Chooses a line for this personality/species/trigger, or null if the language file has none.
     * The pet's previous line for the trigger and the lines recently seen around the same player
     * are avoided whenever there is an alternative.
     */
    private String pickLine(Personality personality, Species species, Trigger trigger, String last, Set<String> recent) {
        List<String> personalityLines = lines("thoughts." + personality.id() + ".", trigger);
        List<String> speciesLines = lines("species." + species.id() + ".", trigger);

        List<String> pool = personalityLines;
        if (!speciesLines.isEmpty() && (personalityLines.isEmpty()
                || ThreadLocalRandom.current().nextDouble() < platform.settings().speciesLineChance)) {
            pool = speciesLines;
        }
        if (pool.isEmpty()) {
            return null;
        }
        List<String> fresh = new ArrayList<>(pool.size());
        for (String line : pool) {
            if (!line.equals(last) && !recent.contains(line)) {
                fresh.add(line);
            }
        }
        if (fresh.isEmpty()) {
            for (String line : pool) {
                if (!line.equals(last)) {
                    fresh.add(line);
                }
            }
        }
        List<String> from = fresh.isEmpty() ? pool : fresh;
        return from.get(ThreadLocalRandom.current().nextInt(from.size()));
    }

    private List<String> lines(String prefix, Trigger trigger) {
        List<String> found = platform.lang().list(prefix + trigger.id());
        if (found.isEmpty() && trigger.fallback() != null) {
            found = platform.lang().list(prefix + trigger.fallback().id());
        }
        return found;
    }

    // ------------------------------------------------------------------
    //  Pet bookkeeping
    // ------------------------------------------------------------------

    /** Gives the pet a personality if it has none yet. Returns the personality it ends up with. */
    public Personality ensurePersonality(PetView pet) {
        Personality personality = pet.personality();
        if (personality == null) {
            personality = platform.settings().randomPersonality();
            pet.setPersonality(personality);
        }
        return personality;
    }

    private PetState state(UUID pet, long now) {
        PetState state = pets.computeIfAbsent(pet, id -> {
            PetState created = new PetState();
            // A pet seen for the first time waits a little before its first ambient thought, so a
            // whole kennel does not start talking the moment its owner logs in.
            created.nextAmbientAt = now + ThreadLocalRandom.current().nextLong(3_000, 15_000);
            return created;
        });
        state.lastTouched = now;
        return state;
    }

    /** Current mood id for /petthoughts info (see lang "mood.*"). */
    public String mood(PetView pet, OwnerView owner) {
        long now = System.currentTimeMillis();
        PetState state = pets.get(pet.id());
        if (pet.healthRatio() < 0.30) {
            return "hurt";
        }
        if (pet.onFire() || pet.nearestHostile() != null) {
            return "scared";
        }
        if (pet.inWater() || pet.inRain()) {
            return "wet";
        }
        if (state != null && now - state.lastHappyAt < HAPPY_MS) {
            return "happy";
        }
        Species species = pet.species();
        if (owner.distanceTo(pet) <= 6 && (species.likes(owner.mainHand()) || species.likes(owner.offHand()))) {
            return "hungry";
        }
        long since = pet.sitSince();
        if (since > 0 && now - since >= SITTING_LONG_MS) {
            return "bored";
        }
        if (pet.dimension() == Dimension.OVERWORLD && pet.dayTime() >= 13000 && pet.dayTime() < 23000) {
            return "sleepy";
        }
        return "content";
    }

    /** Last line this pet thought, or null. */
    public String lastThought(UUID pet) {
        PetState state = pets.get(pet);
        return state == null ? null : state.lastThought;
    }

    public void forgetPet(UUID pet) {
        pets.remove(pet);
    }

    public void forgetOwner(UUID owner) {
        owners.remove(owner);
    }

    /** Drops state of pets nobody has scanned for a while. Call it from a slow repeating task. */
    public void purge() {
        long limit = System.currentTimeMillis() - STATE_TTL_MS;
        pets.values().removeIf(state -> state.lastTouched < limit);
    }

    public void reset() {
        pets.clear();
        owners.clear();
    }
}
