package dev.petthoughts.core.thought;

import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import dev.petthoughts.core.platform.Platform;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Event triggers. Each platform listens to its own game events and calls one of these methods;
 * the decisions (chances, delays, how many pets react) are shared.
 */
public final class PetEvents {

    private static final Set<String> TREASURE = Set.of(
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "ancient_debris");
    private static final double NEAR = 12.0;

    private final Platform platform;
    private final ThoughtDirector director;

    public PetEvents(Platform platform, ThoughtDirector director) {
        this.platform = platform;
        this.director = director;
    }

    /** One pet reacts a few ticks later, so the bubble appears after the thing it reacts to. */
    private void reactLater(PetView pet, OwnerView owner, Trigger trigger, ThoughtVars vars, int delayTicks) {
        platform.later(pet, delayTicks, () -> director.react(pet, owner, trigger, vars));
    }

    /**
     * Something happened to the owner: pets close to the given point may comment. The chance is
     * rolled once per event and the nearest pets go first (the director limits how many speak).
     */
    private void reactNearby(OwnerView owner, double x, double y, double z, Trigger trigger, ThoughtVars vars, int delay) {
        if (platform.settings().triggerDisabled(trigger) || !ThoughtDirector.roll(trigger)) {
            return;
        }
        List<PetView> pets = new java.util.ArrayList<>(platform.petsNear(owner, x, y, z, NEAR));
        pets.sort(Comparator.comparingDouble(pet -> {
            double dx = pet.x() - x;
            double dy = pet.y() - y;
            double dz = pet.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }));
        int limit = Math.min(pets.size(), platform.settings().maxSameReaction + 1);
        for (int i = 0; i < limit; i++) {
            reactLater(pets.get(i), owner, trigger, vars, delay + i * 6);
        }
    }

    private void reactNearby(OwnerView owner, Trigger trigger, ThoughtVars vars) {
        reactNearby(owner, owner.x(), owner.y(), owner.z(), trigger, vars, 4);
    }

    // ---- things that happen to one pet ----

    public void tamed(PetView pet, OwnerView owner) {
        director.ensurePersonality(pet);
        reactLater(pet, owner, Trigger.TAMED, ThoughtVars.NONE, 8);
    }

    public void fed(PetView pet, OwnerView owner, String foodId) {
        reactLater(pet, owner, Trigger.FED, ThoughtVars.food(foodId), 1);
    }

    /** Petting answers immediately. Must already be on the pet's thread. */
    public void petted(PetView pet, OwnerView owner) {
        director.react(pet, owner, Trigger.PETTED, ThoughtVars.NONE);
    }

    public void petHurt(PetView pet, OwnerView owner, boolean byOwner) {
        Trigger trigger = byOwner ? Trigger.HIT_BY_OWNER : Trigger.PET_HURT;
        if (ThoughtDirector.roll(trigger)) {
            reactLater(pet, owner, trigger, ThoughtVars.NONE, 2);
        }
    }

    public void petKilledMob(PetView pet, OwnerView owner, String mobName) {
        if (ThoughtDirector.roll(Trigger.PET_KILLED_MOB)) {
            reactLater(pet, owner, Trigger.PET_KILLED_MOB, ThoughtVars.mob(mobName), 5);
        }
    }

    public void petDied(PetView pet) {
        platform.removeBubble(pet.id());
        director.forgetPet(pet.id());
    }

    public void teleported(PetView pet, OwnerView owner) {
        if (ThoughtDirector.roll(Trigger.TELEPORTED)) {
            reactLater(pet, owner, Trigger.TELEPORTED, ThoughtVars.NONE, 10);
        }
    }

    /** @param first true for the first parent, so both parents do not speak at the same instant */
    public void hadBaby(PetView parent, OwnerView owner, boolean first) {
        reactLater(parent, owner, Trigger.HAD_BABY, ThoughtVars.NONE, first ? 10 : 30);
    }

    /** An explosion went off near this pet. */
    public void explosionNear(PetView pet, OwnerView owner) {
        if (!platform.settings().triggerDisabled(Trigger.EXPLOSION)) {
            reactLater(pet, owner, Trigger.EXPLOSION, ThoughtVars.NONE, 6);
        }
    }

    // ---- things the owner does (nearby pets may comment) ----

    public void ownerHurt(OwnerView owner) {
        reactNearby(owner, Trigger.OWNER_HURT, ThoughtVars.NONE);
    }

    public void ownerDied(OwnerView owner) {
        reactNearby(owner, Trigger.OWNER_DIED, ThoughtVars.NONE);
    }

    public void ownerKilledMob(OwnerView owner, String mobName) {
        reactNearby(owner, Trigger.OWNER_KILLED_MOB, ThoughtVars.mob(mobName));
    }

    public void ownerAte(OwnerView owner, String foodId) {
        reactNearby(owner, Trigger.OWNER_ATE, ThoughtVars.food(foodId));
    }

    public void ownerFished(OwnerView owner) {
        reactNearby(owner, Trigger.OWNER_FISHED, ThoughtVars.NONE);
    }

    public void ownerBrokeBlock(OwnerView owner, String blockId) {
        if (TREASURE.contains(blockId)) {
            reactNearby(owner, Trigger.OWNER_FOUND_TREASURE, ThoughtVars.NONE);
        }
    }

    public void ownerAdvancement(OwnerView owner) {
        // Later than usual: taming a pet also grants an advancement, and "I have a human!" must win.
        reactNearby(owner, owner.x(), owner.y(), owner.z(), Trigger.OWNER_ADVANCEMENT, ThoughtVars.NONE, 30);
    }

    public void ownerPlayedMusic(OwnerView owner, double x, double y, double z) {
        reactNearby(owner, x, y, z, Trigger.MUSIC, ThoughtVars.NONE, 10);
    }
}
