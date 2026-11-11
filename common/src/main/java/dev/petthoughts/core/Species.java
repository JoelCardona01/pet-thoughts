package dev.petthoughts.core;

import java.util.Locale;
import java.util.Set;

/**
 * Supported pet species with the food each one likes. Item ids are plain lowercase names
 * ("cooked_beef"), which is what both Bukkit materials and vanilla registry paths reduce to.
 */
public enum Species {
    WOLF(false, "bone", "beef", "cooked_beef", "porkchop", "cooked_porkchop", "chicken", "cooked_chicken",
            "mutton", "cooked_mutton", "rabbit", "cooked_rabbit", "rotten_flesh", "cod", "cooked_cod",
            "salmon", "cooked_salmon", "rabbit_stew"),
    CAT(false, "cod", "salmon", "tropical_fish"),
    PARROT(false, "wheat_seeds", "melon_seeds", "pumpkin_seeds", "beetroot_seeds", "torchflower_seeds", "pitcher_pod"),
    HORSE(true, "sugar", "apple", "wheat", "hay_block", "golden_carrot", "golden_apple", "enchanted_golden_apple"),
    DONKEY(true, "sugar", "apple", "wheat", "hay_block", "golden_carrot", "golden_apple", "enchanted_golden_apple"),
    MULE(true, "sugar", "apple", "wheat", "hay_block", "golden_carrot", "golden_apple", "enchanted_golden_apple"),
    LLAMA(true, "wheat", "hay_block");

    private final String id = name().toLowerCase(Locale.ROOT);
    private final boolean rideable;
    private final Set<String> likedFood;

    Species(boolean rideable, String... food) {
        this.rideable = rideable;
        this.likedFood = Set.of(food);
    }

    public String id() {
        return id;
    }

    /** Rideable species never get a passenger bubble: an extra passenger would block mounting. */
    public boolean rideable() {
        return rideable;
    }

    /**
     * Blocks between the top of the hitbox and the tip of the bubble's tail (when the bubble follows the pet instead of riding it). Horses and llamas carry their head well
     * above their hitbox, so their bubble needs to float higher.
     */
    public float bubbleGap() {
        return switch (this) {
            case HORSE, DONKEY, MULE -> 0.45f;
            case LLAMA -> 0.3f;
            default -> 0.1f;
        };
    }

    public boolean likes(String itemId) {
        return itemId != null && likedFood.contains(itemId);
    }

    /** Food that is actually eaten when given (a tamed wolf likes bones but does not eat them). */
    public boolean eats(String itemId) {
        return likes(itemId) && !(this == WOLF && "bone".equals(itemId));
    }
}
