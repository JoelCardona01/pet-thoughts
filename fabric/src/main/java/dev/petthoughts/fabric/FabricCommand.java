package dev.petthoughts.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.petthoughts.core.command.Actor;
import dev.petthoughts.core.command.Commands;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Brigadier side of /petthoughts and /pt: the logic itself is the shared {@link Commands}. */
final class FabricCommand {

    private static final int LOOK_RANGE = 8;

    private FabricCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, PetThoughtsMod mod, Commands commands) {
        for (String label : new String[] {"petthoughts", "pt"}) {
            LiteralArgumentBuilder<CommandSourceStack> root = LiteralArgumentBuilder.literal(label);
            root.executes(context -> run(mod, commands, context.getSource(), label, ""));
            RequiredArgumentBuilder<CommandSourceStack, String> args =
                    RequiredArgumentBuilder.argument("args", StringArgumentType.greedyString());
            args.suggests((context, builder) -> {
                String typed = builder.getRemaining();
                String[] parts = typed.split(" ", -1);
                // Suggestions replace only the word being typed.
                SuggestionsBuilder word = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
                for (String option : commands.complete(new SourceActor(mod, context.getSource()), parts)) {
                    word.suggest(option);
                }
                return word.buildFuture();
            });
            args.executes(context -> run(mod, commands, context.getSource(), label, StringArgumentType.getString(context, "args")));
            dispatcher.register(root.then(args));
        }
    }

    private static int run(PetThoughtsMod mod, Commands commands, CommandSourceStack source, String label, String args) {
        String trimmed = args.trim();
        commands.run(new SourceActor(mod, source), label, trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+"));
        return 1;
    }

    private record SourceActor(PetThoughtsMod mod, CommandSourceStack source) implements Actor {

        private ServerPlayer player() {
            return source.getPlayer();
        }

        @Override
        public boolean isPlayer() {
            return source.getPlayer() != null;
        }

        /**
         * Fabric has no permission plugin: player features are open to everyone, admin features
         * need operator level 2 (or being the host of a single-player world).
         */
        @Override
        public boolean has(String permission) {
            if (!Commands.ADMIN.equals(permission)) {
                return true;
            }
            if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                return true;
            }
            ServerPlayer player = player();
            return player != null && source.getServer().isSingleplayerOwner(player.nameAndId());
        }

        @Override
        public void send(String langKey, Map<String, Object> vars) {
            Component message = FabricText.message(mod.lang(), langKey, vars);
            if (Commands.prefixed(langKey)) {
                message = Component.empty().append(FabricText.message(mod.lang(), "messages.prefix", Map.of())).append(message);
            }
            source.sendSystemMessage(message);
        }

        @Override
        public OwnerView view() {
            return FabricPet.owner(player());
        }

        @Override
        public PetView lookedAtPet() {
            return FabricEvents.lookedAt(mod, player(), LOOK_RANGE);
        }

        @Override
        public boolean bubblesHidden() {
            return mod.isHidden(player().getUUID());
        }

        @Override
        public void setBubblesHidden(boolean hidden) {
            ServerPlayer player = player();
            if (hidden) {
                player.setAttached(FabricPet.HIDDEN, true);
            } else {
                player.removeAttached(FabricPet.HIDDEN);
            }
            mod.setHidden(player.getUUID(), hidden);
        }
    }
}
