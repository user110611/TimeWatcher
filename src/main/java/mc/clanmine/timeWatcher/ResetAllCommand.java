package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResetAllCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public ResetAllCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("timewatcher.admin")) {
            sender.sendMessage("§cНет доступа!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cИспользование: §f/resetall <ник>");
            return true;
        }

        String nick = args[0];
        boolean success = timeManager.resetPlayer(nick);

        if (success) {
            sender.sendMessage("§a✔ Статистика игрока §f" + nick + " §aсброшена!");
        } else {
            sender.sendMessage("§cИгрок §f" + nick + " §cне найден в базе данных!");
        }

        return true;
    }
}