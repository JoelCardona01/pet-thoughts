package dev.petthoughts.fabric;

import dev.petthoughts.core.bubble.Bubble;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.text.MiniText;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;

/** Text for chat messages and bubbles, as vanilla components (basic tags only, see {@link MiniText}). */
final class FabricText {

    private FabricText() {
    }

    static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    /** A mob's name in the configured language (or its name tag). */
    static String entityName(LangStore lang, Entity entity) {
        Component custom = entity.getCustomName();
        return Names.mob(lang, typeId(entity), custom == null ? null : custom.getString());
    }

    static Component render(String raw, Map<String, Object> vars) {
        MutableComponent out = Component.empty();
        for (MiniText.Span span : MiniText.parse(raw, vars)) {
            MutableComponent part = span.value() instanceof Component component
                    ? Component.empty().append(component)
                    : Component.literal(String.valueOf(span.value()));
            out.append(part.withStyle(style(span.color(), span.bold(), span.italic())));
        }
        return out;
    }

    static Component message(LangStore lang, String key, Map<String, Object> vars) {
        return render(lang.raw(key), vars);
    }

    /** The bubble text: its lines joined with newlines, in the bubble's text colour. */
    static Component bubble(Bubble bubble) {
        MutableComponent out = Component.empty().withColor(bubble.textRgb());
        List<List<Bubble.Run>> lines = bubble.lines();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append("\n");
            }
            for (Bubble.Run run : lines.get(i)) {
                out.append(Component.literal(run.text()).withStyle(style(run.rgb(), run.bold(), run.italic())));
            }
        }
        return out;
    }

    private static Style style(Integer rgb, boolean bold, boolean italic) {
        Style style = Style.EMPTY.withBold(bold).withItalic(italic);
        return rgb == null ? style : style.withColor(rgb);
    }
}
