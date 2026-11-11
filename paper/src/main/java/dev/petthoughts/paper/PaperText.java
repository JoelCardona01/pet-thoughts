package dev.petthoughts.paper;

import dev.petthoughts.core.bubble.Bubble;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.config.LangStore;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;

/** Text for chat (full MiniMessage) and for bubbles (pre-wrapped runs from the designer). */
final class PaperText {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private PaperText() {
    }

    /** A mob's name in the configured language (or its name tag). */
    static String entityName(LangStore lang, Entity entity) {
        Component custom = entity.customName();
        String plain = custom == null ? null : PlainTextComponentSerializer.plainText().serialize(custom);
        return Names.mob(lang, entity.getType().getKey().getKey(), plain);
    }

    /** A language entry with its placeholders filled in. Strings are inserted as plain text. */
    static Component message(LangStore lang, String key, Map<String, Object> vars) {
        TagResolver.Builder resolvers = TagResolver.builder();
        for (Map.Entry<String, Object> var : vars.entrySet()) {
            resolvers.resolver(var.getValue() instanceof Component component
                    ? Placeholder.component(var.getKey(), component)
                    : Placeholder.unparsed(var.getKey(), String.valueOf(var.getValue())));
        }
        return MINI.deserialize(lang.raw(key), resolvers.build());
    }

    /** The bubble text: its lines joined with newlines, in the bubble's text colour. */
    static Component bubble(Bubble bubble) {
        TextComponent.Builder out = Component.text().color(TextColor.color(bubble.textRgb()));
        List<List<Bubble.Run>> lines = bubble.lines();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append(Component.newline());
            }
            for (Bubble.Run run : lines.get(i)) {
                TextComponent part = Component.text(run.text())
                        .decoration(TextDecoration.BOLD, run.bold())
                        .decoration(TextDecoration.ITALIC, run.italic());
                out.append(run.rgb() == null ? part : part.color(TextColor.color(run.rgb())));
            }
        }
        return out.build();
    }
}
