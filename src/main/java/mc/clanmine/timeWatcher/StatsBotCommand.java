package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class StatsBotCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public StatsBotCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("timewatcher.admin")) {
            sender.sendMessage("§cНет доступа!");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§cИспользование: §f/statsbot reload");
            return true;
        }

        timeManager.reload();
        sender.sendMessage("§a✔ База данных перезагружена!");
        return true;
    }
}