package dev.petthoughts.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * config.yml and lang/*.yml on disk, shared by the plugin and the mod.
 *
 * <p>Defaults live inside the jar under {@code petthoughts/} and are copied to the data folder the
 * first time, so they can be edited. Language lookups fall back from the edited file to the bundled
 * file of the same language and then to bundled English: a file written for an older version never
 * produces missing text.
 *
 * <p>With {@code language: auto} every player gets the file that matches the language of their
 * game client (which follows the language of their computer unless they changed it in Minecraft).
 * "pt_br" uses lang/pt_br.yml, then any lang/pt*.yml, then {@code fallback-language}.
 */
public final class DataFiles {

    private static final String BUNDLE = "petthoughts/";
    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "es", "pt_br", "fr", "de", "ru", "pl", "zh_cn");

    private final Path folder;
    private final ClassLoader loader;
    private final Consumer<String> warn;
    private Settings settings;
    private LangStore lang;
    private boolean perPlayer;
    /** Every lang/*.yml in the data folder, by code ("pt_br"). */
    private Map<String, LangStore> languages = Map.of();
    /** Client locale ("pt_br") -> language picked for it. Cleared on reload. */
    private final Map<String, LangStore> byLocale = new ConcurrentHashMap<>();

    public DataFiles(Path folder, ClassLoader loader, Consumer<String> warn) {
        this.folder = folder;
        this.loader = loader;
        this.warn = warn;
        reload();
    }

    /** (Re)reads everything from disk. */
    public void reload() {
        copyDefault("config.yml");
        for (String language : BUNDLED_LANGUAGES) {
            copyDefault("lang/" + language + ".yml");
        }
        settings = new Settings(readFile(folder.resolve("config.yml")), warn);

        YamlTree english = readBundled("lang/en.yml");
        Map<String, LangStore> found = new TreeMap<>();
        for (String code : languageFiles()) {
            YamlTree same = BUNDLED_LANGUAGES.contains(code) ? readBundled("lang/" + code + ".yml") : english;
            found.put(code, new LangStore(readFile(folder.resolve("lang/" + code + ".yml")), same, english));
        }
        if (!found.containsKey("en")) {
            found.put("en", new LangStore(YamlTree.EMPTY, english, english));
        }
        languages = Collections.unmodifiableMap(found);
        byLocale.clear();

        perPlayer = settings.language.equals("auto");
        String code = perPlayer ? settings.fallbackLanguage : settings.language;
        lang = languages.get(code);
        if (lang == null) {
            warn.accept("Language file lang/" + code + ".yml not found, using English.");
            lang = languages.get("en");
        }
    }

    /** Codes of the lang/*.yml files in the data folder ("en", "pt_br"...). */
    private List<String> languageFiles() {
        List<String> codes = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder.resolve("lang"))) {
            files.forEach(file -> {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file) && name.endsWith(".yml")) {
                    codes.add(name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT));
                }
            });
        } catch (IOException e) {
            warn.accept("Could not list the language files: " + e.getMessage());
        }
        return codes;
    }

    public Settings settings() {
        return settings;
    }

    /** The server's language: {@code language}, or {@code fallback-language} when it is "auto". */
    public LangStore lang() {
        return lang;
    }

    /**
     * The language for a player whose game client uses {@code locale} ("en_us", "pt_BR"...). Null
     * (console, offline player) or a fixed {@code language} give the server's language.
     */
    public LangStore lang(String locale) {
        if (!perPlayer || locale == null || locale.isBlank()) {
            return lang;
        }
        return byLocale.computeIfAbsent(locale, this::match);
    }

    private LangStore match(String locale) {
        String code = locale.toLowerCase(Locale.ROOT).replace('-', '_');
        LangStore exact = languages.get(code);
        if (exact != null) {
            return exact;
        }
        int cut = code.indexOf('_');
        String base = cut < 0 ? code : code.substring(0, cut);
        LangStore same = languages.get(base);
        if (same != null) {
            return same;
        }
        for (Map.Entry<String, LangStore> entry : languages.entrySet()) {
            if (entry.getKey().startsWith(base + "_")) {
                return entry.getValue();
            }
        }
        return lang;
    }

    private void copyDefault(String name) {
        Path target = folder.resolve(name);
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = loader.getResourceAsStream(BUNDLE + name)) {
            if (in != null) {
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
            }
        } catch (IOException e) {
            warn.accept("Could not create " + target + ": " + e.getMessage());
        }
    }

    private YamlTree readFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return YamlTree.EMPTY;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return YamlTree.parse(reader, problem -> warn.accept(file.getFileName() + " has a YAML error, "
                    + "defaults are used until it is fixed: " + problem));
        } catch (IOException e) {
            warn.accept("Could not read " + file + ": " + e.getMessage());
            return YamlTree.EMPTY;
        }
    }

    private YamlTree readBundled(String name) {
        try (InputStream in = loader.getResourceAsStream(BUNDLE + name)) {
            if (in == null) {
                return YamlTree.EMPTY;
            }
            return YamlTree.parse(new InputStreamReader(in, StandardCharsets.UTF_8), problem -> { });
        } catch (IOException e) {
            return YamlTree.EMPTY;
        }
    }
}
