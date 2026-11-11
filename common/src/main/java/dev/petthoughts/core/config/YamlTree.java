package dev.petthoughts.core.config;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** A parsed YAML file read with dotted paths ("bubble-style.background"). Never throws on bad input. */
public final class YamlTree {

    public static final YamlTree EMPTY = new YamlTree(Map.of());

    private final Map<?, ?> root;

    private YamlTree(Map<?, ?> root) {
        this.root = root;
    }

    /** Parses YAML; a broken file yields an empty tree and the error message through {@code problem}. */
    public static YamlTree parse(Reader reader, java.util.function.Consumer<String> problem) {
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            return loaded instanceof Map<?, ?> map ? new YamlTree(map) : EMPTY;
        } catch (RuntimeException e) {
            problem.accept(e.getMessage());
            return EMPTY;
        }
    }

    private Object get(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            Object next = map.get(part);
            if (next == null) {
                // Keys like "true"/"1" are parsed as non-strings by YAML.
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (part.equals(String.valueOf(entry.getKey()))) {
                        next = entry.getValue();
                        break;
                    }
                }
            }
            current = next;
        }
        return current;
    }

    public String string(String path) {
        Object value = get(path);
        return value == null || value instanceof Map || value instanceof List ? null : String.valueOf(value);
    }

    public String string(String path, String fallback) {
        String value = string(path);
        return value == null ? fallback : value;
    }

    public double number(String path, double fallback) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean bool(String path, boolean fallback) {
        Object value = get(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    public boolean isList(String path) {
        return get(path) instanceof List;
    }

    public List<String> strings(String path) {
        if (!(get(path) instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
