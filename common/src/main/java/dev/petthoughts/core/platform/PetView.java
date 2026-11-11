package dev.petthoughts.core.platform;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Species;
import java.util.UUID;

/**
 * A live tamed pet as the brain sees it. Implemented by each platform on top of its own entity
 * class. Only valid on the thread that owns the pet.
 */
public interface PetView {

    enum Dimension { OVERWORLD, NETHER, END, OTHER }

    /** A hostile mob the pet can see, with its name already in the configured language. */
    record Hostile(boolean creeper, String name) {}

    UUID id();

    UUID ownerId();

    Species species();

    /** The pet's name as the platform's text object (name tag, or the translated species). */
    Object name();

    String plainName();

    boolean alive();

    boolean invisible();

    double healthRatio();

    int health();

    int maxHealth();

    boolean onFire();

    boolean inWater();

    boolean inRain();

    /** True/false for species that can sit, null for the ones that cannot. */
    Boolean sitting();

    boolean inBoat();

    boolean leashed();

    boolean riddenBy(UUID player);

    // ---- where and when ----

    String worldId();

    double x();

    double y();

    double z();

    Dimension dimension();

    /** Time of day, 0-23999. */
    long dayTime();

    /** Total world time in ticks (used to tell days apart). */
    long gameTime();

    boolean storming();

    boolean thundering();

    int skyLight();

    /** Biome temperature at the pet (below 0.15 = snowy, 1.5+ = desert-like). */
    double temperature();

    /** Nearest hostile within 8 blocks that the pet has line of sight to, or null. */
    Hostile nearestHostile();

    // ---- data stored on the pet itself (survives restarts) ----

    Personality personality();

    void setPersonality(Personality personality);

    boolean muted();

    void setMuted(boolean muted);

    /** Last time (epoch ms) the owner was close; 0 if never recorded. */
    long ownerSeen();

    void setOwnerSeen(long epochMs);

    /** When (epoch ms) the pet sat down; 0 if it is not sitting. */
    long sitSince();

    void setSitSince(long epochMs);
}
