package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class StatsModeCommand implements CommandExecutor, TabCompleter {

    private final TimeManager timeManager;

    public StatsModeCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (args.length == 0) {
            String current = timeManager.getMode(player);
            player.sendMessage("§7Текущий режим: §e" + current);
            player.sendMessage("§7Используй: §f/mystatsmode <chat|menu>");
            return true;
        }

        String mode = args[0].toLowerCase();
        if (!mode.equals("chat") && !mode.equals("menu")) {
            player.sendMessage("§cНеверный режим! Используй: §fchat §cили §fmenu");
            return true;
        }

        timeManager.setMode(player, mode);
        player.sendMessage("§aРежим переключён на: §e" + mode);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("chat", "menu");
        }
        return List.of();
    }
}