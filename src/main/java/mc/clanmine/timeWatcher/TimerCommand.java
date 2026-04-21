package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TimerCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public TimerCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }

        long millis = timeManager.getMillisUntilNextToken(player);

        if (millis < 0) {
            player.sendMessage("§cНе удалось получить данные таймера.");
            return true;
        }

        if (millis == 0) {
            player.sendMessage("§aТы уже можешь получить токен! Просто оставайся онлайн.");
            return true;
        }

        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        player.sendMessage("§6⏱ До следующего начисления токена: §e" + minutes + "м " + seconds + "с");
        player.sendMessage("§7(Токены начисляются каждые 30 минут игры онлайн)");
        return true;
    }
}