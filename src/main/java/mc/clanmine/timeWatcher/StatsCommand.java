package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public StatsCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        String mode = timeManager.getMode(player);

        if (mode.equals("menu")) {
            StatsGUI.open(player, timeManager);
        } else {
            long totalSeconds = timeManager.getSeconds(player);
            long hours   = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            double tokens = timeManager.getTokens(player);
            String tokenColor = tokens < 0 ? "§c" : "§b";

            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e  Статистика §f" + player.getName());
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§7  Часы:    §e" + hours + " ч.");
            player.sendMessage("§7  Минуты:  §e" + minutes + " мин.");
            player.sendMessage("§7  Секунды: §e" + seconds + " сек.");
            player.sendMessage("§7  Токены:  " + tokenColor + String.format("%.2f", tokens));
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━");
        }

        return true;
    }
}