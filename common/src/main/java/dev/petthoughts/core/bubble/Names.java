package dev.petthoughts.core.bubble;

import dev.petthoughts.core.config.LangStore;
import java.util.Locale;
import java.util.Set;

/**
 * Names of mobs and food as plain text in the configured language. Bubbles are measured on the
 * server, so they cannot use client-side translations.
 */
public final class Names {

    /** Mobs that count as "Enemy" in code but do not attack unless provoked. */
    private static final Set<String> NOT_SCARY = Set.of("enderman", "piglin", "zombified_piglin");

    private Names() {
    }

    public static boolean scary(String entityTypeId) {
        return !NOT_SCARY.contains(entityTypeId);
    }

    /** A mob's name tag, or its type name from lang (names.mob.*), or its id made readable. */
    public static String mob(LangStore lang, String typeId, String customName) {
        if (customName != null && !customName.isBlank()) {
            return customName;
        }
        String name = lang.rawOrNull("names.mob." + typeId);
        return name != null ? name : readable(typeId);
    }

    /** A food item from lang (names.food.*), or its id made readable; the generic word if there is none. */
    public static String food(LangStore lang, String itemId) {
        if (itemId == null || itemId.isEmpty() || itemId.equals("air")) {
            return lang.raw("generic.food");
        }
        String name = lang.rawOrNull("names.food." + itemId);
        return name != null ? name : readable(itemId);
    }

    /** "cave_spider" -> "Cave spider". */
    static String readable(String id) {
        String text = id.replace('_', ' ');
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
