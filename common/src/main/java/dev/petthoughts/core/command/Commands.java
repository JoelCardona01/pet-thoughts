package dev.petthoughts.core.command;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import dev.petthoughts.core.platform.Platform;
import dev.petthoughts.core.thought.ThoughtDirector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /petthoughts (alias /pt), identical on every platform. */
public final class Commands {

    public static final String ADMIN = "petthoughts.admin";
    public static final String TOGGLE = "petthoughts.toggle";
    public static final String INFO = "petthoughts.info";
    public static final String MUTE = "petthoughts.mute";

    private final Platform platform;
    private final ThoughtDirector director;

    public Commands(Platform platform, ThoughtDirector director) {
        this.platform = platform;
        this.director = director;
    }

    /** Multi-line outputs (help, info) are sent without the chat prefix; everything else gets it. */
    public static boolean prefixed(String langKey) {
        return !langKey.startsWith("messages.help.") && !langKey.startsWith("messages.info.");
    }

    public void run(Actor actor, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> toggle(actor);
            case "info" -> info(actor);
            case "mute" -> mute(actor);
            case "set" -> set(actor, args);
            case "test" -> test(actor, args);
            case "reload" -> reload(actor);
            default -> help(actor, label);
        }
    }

    public List<String> complete(Actor actor, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = actor.has(ADMIN);
        if (args.length <= 1) {
            options.addAll(List.of("help", "toggle", "info", "mute"));
            if (admin) {
                options.addAll(List.of("set", "test", "reload"));
            }
        } else if (args.length == 2 && admin && args[0].equalsIgnoreCase("set")) {
            options.addAll(personalityIds());
        } else if (args.length == 2 && admin && args[0].equalsIgnoreCase("test")) {
            for (Trigger t : Trigger.values()) {
                options.add(t.id());
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> vars(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private boolean allowed(Actor actor, String permission) {
        if (actor.has(permission)) {
            return true;
        }
        actor.send("messages.no-permission", Map.of());
        return false;
    }

    private boolean player(Actor actor) {
        if (actor.isPlayer()) {
            return true;
        }
        actor.send("messages.players-only", Map.of());
        return false;
    }

    /** The pet the actor looks at, or null after telling them what is wrong. */
    private PetView target(Actor actor, boolean mustOwn) {
        PetView pet = actor.lookedAtPet();
        if (pet == null) {
            actor.send("messages.look-at-pet", Map.of());
            return null;
        }
        if (mustOwn && !actor.view().id().equals(pet.ownerId()) && !actor.has(ADMIN)) {
            actor.send("messages.not-your-pet", Map.of());
            return null;
        }
        return pet;
    }

    private void help(Actor actor, String label) {
        Map<String, Object> vars = vars("label", label);
        actor.send("messages.help.header", vars);
        actor.send("messages.help.toggle", vars);
        actor.send("messages.help.info", vars);
        actor.send("messages.help.mute", vars);
        if (platform.settings().pettingEnabled) {
            actor.send("messages.help.petting", vars);
        }
        if (actor.has(ADMIN)) {
            actor.send("messages.help.set", vars);
            actor.send("messages.help.test", vars);
            actor.send("messages.help.reload", vars);
        }
    }

    private void toggle(Actor actor) {
        if (!player(actor) || !allowed(actor, TOGGLE)) {
            return;
        }
        boolean hide = !actor.bubblesHidden();
        actor.setBubblesHidden(hide);
        actor.send(hide ? "messages.toggle-off" : "messages.toggle-on", Map.of());
    }

    private void info(Actor actor) {
        if (!player(actor) || !allowed(actor, INFO)) {
            return;
        }
        PetView pet = target(actor, true);
        if (pet == null) {
            return;
        }
        LangStore lang = platform.lang();
        OwnerView owner = actor.view();
        Personality personality = director.ensurePersonality(pet);
        String last = director.lastThought(pet.id());
        String thought = last == null ? lang.raw("messages.info.no-thought") : last
                .replace("<owner>", owner.name())
                .replace("<pet>", pet.plainName())
                .replace("<mob>", lang.raw("generic.mob"))
                .replace("<friend>", lang.raw("generic.friend"))
                .replace("<food>", lang.raw("generic.food"));
        Map<String, Object> vars = vars(
                "pet", pet.name(),
                "species", lang.raw("species-names." + pet.species().id()),
                "personality", lang.raw("personality." + personality.id() + ".name"),
                "description", lang.raw("personality." + personality.id() + ".description"),
                "mood", lang.raw("mood." + director.mood(pet, owner)),
                "health", String.valueOf(pet.health()),
                "max_health", String.valueOf(pet.maxHealth()),
                "thought", thought);
        actor.send("messages.info.header", vars);
        actor.send("messages.info.personality", vars);
        actor.send("messages.info.mood", vars);
        actor.send("messages.info.health", vars);
        actor.send("messages.info.last-thought", vars);
        if (pet.muted()) {
            actor.send("messages.info.muted", vars);
        }
    }

    private void mute(Actor actor) {
        if (!player(actor) || !allowed(actor, MUTE)) {
            return;
        }
        PetView pet = target(actor, true);
        if (pet == null) {
            return;
        }
        boolean mute = !pet.muted();
        pet.setMuted(mute);
        if (mute) {
            platform.removeBubble(pet.id());
        }
        actor.send(mute ? "messages.mute-on" : "messages.mute-off", vars("pet", pet.name()));
    }

    private void set(Actor actor, String[] args) {
        if (!player(actor) || !allowed(actor, ADMIN)) {
            return;
        }
        Personality personality = args.length < 2 ? null : Personality.byId(args[1]);
        if (personality == null) {
            actor.send("messages.unknown-personality", vars("list", String.join(", ", personalityIds())));
            return;
        }
        PetView pet = target(actor, false);
        if (pet == null) {
            return;
        }
        pet.setPersonality(personality);
        actor.send("messages.set-done", vars("pet", pet.name(),
                "personality", platform.lang().raw("personality." + personality.id() + ".name")));
    }

    private void test(Actor actor, String[] args) {
        if (!player(actor) || !allowed(actor, ADMIN)) {
            return;
        }
        Trigger trigger = args.length < 2 ? null : Trigger.byId(args[1]);
        if (trigger == null) {
            actor.send("messages.unknown-trigger", Map.of());
            return;
        }
        PetView pet = target(actor, false);
        if (pet == null) {
            return;
        }
        ThoughtDirector.Result result = director.force(pet, actor.view(), trigger);
        actor.send(result == ThoughtDirector.Result.NO_LINE ? "messages.test-no-line" : "messages.test-done",
                vars("pet", pet.name(), "trigger", trigger.id()));
    }

    private void reload(Actor actor) {
        if (!allowed(actor, ADMIN)) {
            return;
        }
        platform.reload();
        actor.send("messages.reloaded", Map.of());
    }

    private static List<String> personalityIds() {
        List<String> ids = new ArrayList<>();
        for (Personality p : Personality.values()) {
            ids.add(p.id());
        }
        return ids;
    }
}
