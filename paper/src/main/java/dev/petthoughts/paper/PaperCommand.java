package dev.petthoughts.paper;

import dev.petthoughts.core.command.Actor;
import dev.petthoughts.core.command.Commands;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Bukkit side of /petthoughts: the logic itself is the shared {@link Commands}. */
final class PaperCommand implements CommandExecutor, TabCompleter {

    private static final int LOOK_RANGE = 8;

    private final PetThoughtsPlugin plugin;
    private final Commands commands;

    PaperCommand(PetThoughtsPlugin plugin, Commands commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        commands.run(new SenderActor(sender), label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return commands.complete(new SenderActor(sender), args);
    }

    private final class SenderActor implements Actor {

        private final CommandSender sender;

        SenderActor(CommandSender sender) {
            this.sender = sender;
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public boolean has(String permission) {
            return sender.hasPermission(permission);
        }

        @Override
        public void send(String langKey, Map<String, Object> vars) {
            Component message = PaperText.message(plugin.lang(), langKey, vars);
            if (Commands.prefixed(langKey)) {
                message = PaperText.message(plugin.lang(), "messages.prefix", Map.of()).append(message);
            }
            sender.sendMessage(message);
        }

        @Override
        public OwnerView view() {
            return PaperPet.owner((Player) sender);
        }

        @Override
        public PetView lookedAtPet() {
            return PaperPet.of(plugin, ((Player) sender).getTargetEntity(LOOK_RANGE));
        }

        @Override
        public boolean bubblesHidden() {
            return plugin.isHidden(((Player) sender).getUniqueId());
        }

        @Override
        public void setBubblesHidden(boolean hidden) {
            Player player = (Player) sender;
            if (hidden) {
                player.getPersistentDataContainer().set(plugin.keys().hidden, PersistentDataType.BYTE, (byte) 1);
            } else {
                player.getPersistentDataContainer().remove(plugin.keys().hidden);
            }
            plugin.setHidden(player.getUniqueId(), hidden);
        }
    }
}
