package dev.petthoughts.core.thought;

import dev.petthoughts.core.Trigger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Anti-spam state shared by all the pets of one player. On Folia several pet threads can touch it
 * at once, so every method is synchronized.
 */
final class OwnerState {

    private static final long CROWD_WINDOW_MS = 10_000;
    private static final int RECENT_LINES = 6;

    private final Map<Trigger, long[]> crowd = new EnumMap<>(Trigger.class); // {windowStart, count}
    private final Deque<String> recentLines = new ArrayDeque<>();
    private long lastBubbleAt;

    synchronized long lastBubbleAt() {
        return lastBubbleAt;
    }

    synchronized void bubbleShown(long now, String line) {
        lastBubbleAt = now;
        recentLines.addLast(line);
        while (recentLines.size() > RECENT_LINES) {
            recentLines.removeFirst();
        }
    }

    synchronized Set<String> recentLines() {
        return new HashSet<>(recentLines);
    }

    /**
     * Group reactions: returns true if one more pet may comment on this trigger right now. At most
     * {@code max} pets get a slot per 10-second window.
     */
    synchronized boolean takeCrowdSlot(Trigger trigger, long now, int max) {
        long[] window = crowd.computeIfAbsent(trigger.group(), t -> new long[2]);
        if (now - window[0] > CROWD_WINDOW_MS) {
            window[0] = now;
            window[1] = 0;
        }
        if (window[1] >= max) {
            return false;
        }
        window[1]++;
        return true;
    }
}
