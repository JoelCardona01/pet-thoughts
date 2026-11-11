package dev.petthoughts.core.config;

import java.util.List;

/** Raw language strings with fallback: edited file, then bundled same language, then bundled English. */
public final class LangStore {

    private final YamlTree active;
    private final YamlTree bundledSame;
    private final YamlTree bundledEnglish;

    LangStore(YamlTree active, YamlTree bundledSame, YamlTree bundledEnglish) {
        this.active = active;
        this.bundledSame = bundledSame;
        this.bundledEnglish = bundledEnglish;
    }

    /** Raw text for a key, or the key itself if it is missing everywhere. */
    public String raw(String key) {
        String value = active.string(key);
        if (value == null) {
            value = bundledSame.string(key);
        }
        if (value == null) {
            value = bundledEnglish.string(key);
        }
        return value == null ? key : value;
    }

    /** Raw text for a key, or null if it is missing everywhere. */
    public String rawOrNull(String key) {
        String value = active.string(key);
        if (value == null) {
            value = bundledSame.string(key);
        }
        return value != null ? value : bundledEnglish.string(key);
    }

    /** Lines for a key; empty if missing everywhere. An explicit empty list ([]) in the edited file is respected. */
    public List<String> list(String key) {
        if (active.isList(key)) {
            return active.strings(key);
        }
        if (bundledSame.isList(key)) {
            return bundledSame.strings(key);
        }
        return bundledEnglish.strings(key);
    }
}
