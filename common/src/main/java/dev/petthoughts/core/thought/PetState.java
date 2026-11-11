package dev.petthoughts.core.thought;

import dev.petthoughts.core.Trigger;
import java.util.EnumMap;
import java.util.Map;
import dev.petthoughts.core.platform.PetView;

/**
 * In-memory state of one pet: cooldowns and "already said it" flags. Losing it on restart is fine;
 * anything that must survive lives in the entity's PDC instead.
 *
 * <p>Only touched from the thread that owns the pet, so it needs no locking.
 */
final class PetState {

    private static final long STREAK_WINDOW_MS = 180_000;

    private final Map<Trigger, Long> cooldownUntil = new EnumMap<>(Trigger.class);
    private final Map<Trigger, String> lastLine = new EnumMap<>(Trigger.class);
    private final Map<Trigger, Integer> streak = new EnumMap<>(Trigger.class);
    private final Map<Trigger, Long> streakAt = new EnumMap<>(Trigger.class);

    long lastTouched;
    long lastBubbleAt;
    long nextAmbientAt;
    long lastHappyAt;
    long returnedUntil;
    long nightSaidDay = -1;
    long dawnSaidDay = -1;
    boolean rainSaid;
    boolean thunderSaid;
    PetView.Dimension dimension;
    /** Sitting state at the previous check; null until the pet has been seen once. */
    Boolean wasSitting;
    String lastThought;

    boolean onCooldown(Trigger trigger, long now) {
        return cooldownUntil.getOrDefault(trigger.group(), 0L) > now;
    }

    void setCooldown(Trigger trigger, long until) {
        cooldownUntil.put(trigger.group(), until);
    }

    void clearCooldown(Trigger trigger) {
        cooldownUntil.remove(trigger.group());
    }

    /**
     * Back-off for urgent thoughts: every time the same danger is announced again within three
     * minutes the cooldown doubles (up to 8x). A zombie that will not go away gets mentioned less
     * and less instead of every 15 seconds forever.
     */
    int bumpStreak(Trigger trigger, long now) {
        Trigger group = trigger.group();
        long last = streakAt.getOrDefault(group, 0L);
        int count = now - last < STREAK_WINDOW_MS ? Math.min(3, streak.getOrDefault(group, 0) + 1) : 0;
        streak.put(group, count);
        streakAt.put(group, now);
        return count;
    }

    String lastLine(Trigger trigger) {
        return lastLine.get(trigger.group());
    }

    void setLastLine(Trigger trigger, String line) {
        lastLine.put(trigger.group(), line);
    }
}
