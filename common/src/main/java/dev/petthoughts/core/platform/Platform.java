package dev.petthoughts.core.platform;

import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.config.Settings;
import dev.petthoughts.core.bubble.Bubble;
import java.util.List;
import java.util.UUID;

/** Everything the brain needs from the game. One implementation per platform (Paper, Fabric). */
public interface Platform {

    Settings settings();

    LangStore lang();

    /** Writes an informational line to the server log. */
    void log(String message);

    /** Reloads config and language from disk and resets runtime state. */
    void reload();

    // ---- bubbles ----

    /** Tier of the bubble the pet is showing, or null if it has none. */
    Trigger.Tier bubbleTier(UUID pet);

    /** Bubbles currently visible around one player's pets. */
    int bubbleCount(UUID owner);

    /** Draws a designed bubble over the pet (replacing its current one). Runs on the pet's thread. */
    void showBubble(PetView pet, OwnerView owner, Bubble bubble);

    void removeBubble(UUID pet);

    // ---- world access ----

    /** Runs the action on the pet's thread after {@code ticks} (at least 1), if the pet still exists. */
    void later(PetView pet, int ticks, Runnable action);

    /** Supported tamed pets of this owner within {@code radius} of a point in the owner's world. */
    List<PetView> petsNear(OwnerView owner, double x, double y, double z, double radius);
}
