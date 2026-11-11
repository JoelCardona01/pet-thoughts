package dev.petthoughts.core.command;

import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.Map;

/** Whoever runs /petthoughts: a player or the console. */
public interface Actor {

    boolean isPlayer();

    /** One of: petthoughts.toggle, .info, .mute, .admin. */
    boolean has(String permission);

    /**
     * Sends the language entry {@code langKey}. Values in {@code vars} fill its placeholders: a
     * String is inserted as plain text, anything else is a text object of the platform.
     */
    void send(String langKey, Map<String, Object> vars);

    /** Snapshot of the player (players only). */
    OwnerView view();

    /** The supported tamed pet the player is looking at (up to 8 blocks), or null. */
    PetView lookedAtPet();

    boolean bubblesHidden();

    /** Stores the player's preference (it survives restarts). */
    void setBubblesHidden(boolean hidden);
}
