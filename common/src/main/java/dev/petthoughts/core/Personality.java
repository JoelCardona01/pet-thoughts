package dev.petthoughts.core;

import java.util.Locale;

/** The five personalities a pet can have. The id is what gets stored on the entity and used in lang keys. */
public enum Personality {
    LOYAL,
    DRAMA,
    GRUMPY,
    PHILOSOPHER,
    GLUTTON;

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() {
        return id;
    }

    public static Personality byId(String id) {
        if (id == null) {
            return null;
        }
        for (Personality p : values()) {
            if (p.id.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }
}
