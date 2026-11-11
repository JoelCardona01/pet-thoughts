package dev.petthoughts.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * config.yml and lang/*.yml on disk, shared by the plugin and the mod.
 *
 * <p>Defaults live inside the jar under {@code petthoughts/} and are copied to the data folder the
 * first time, so they can be edited. Language lookups fall back from the edited file to the bundled
 * file of the same language and then to bundled English: a file written for an older version never
 * produces missing text.
 */
public final class DataFiles {

    private static final String BUNDLE = "petthoughts/";
    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "es");

    private final Path folder;
    private final ClassLoader loader;
    private final Consumer<String> warn;
    private Settings settings;
    private LangStore lang;

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

        String code = settings.language;
        Path file = folder.resolve("lang/" + code + ".yml");
        if (!Files.isRegularFile(file)) {
            warn.accept("Language file lang/" + code + ".yml not found, using English.");
            code = "en";
            file = folder.resolve("lang/en.yml");
        }
        YamlTree english = readBundled("lang/en.yml");
        YamlTree same = BUNDLED_LANGUAGES.contains(code) ? readBundled("lang/" + code + ".yml") : english;
        lang = new LangStore(readFile(file), same, english);
    }

    public Settings settings() {
        return settings;
    }

    public LangStore lang() {
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
