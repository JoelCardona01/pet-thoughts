package dev.petthoughts.core.platform;

import java.util.UUID;

/**
 * What a pet needs to know about its owner, captured at one moment. A snapshot (not a live view)
 * because on Folia pets are evaluated on a different thread than their owner.
 *
 * <p>Held items are plain lowercase ids ("cooked_beef"), "air" for an empty hand.
 */
public record OwnerView(
        UUID id,
        String name,
        String worldId,
        double x,
        double y,
        double z,
        double healthRatio,
        int foodLevel,
        boolean sleeping,
        boolean gliding,
        String mainHand,
        String offHand) {

    /** Distance to a pet, or infinity if it is in another world. */
    public double distanceTo(PetView pet) {
        if (!worldId.equals(pet.worldId())) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = pet.x() - x;
        double dy = pet.y() - y;
        double dz = pet.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
