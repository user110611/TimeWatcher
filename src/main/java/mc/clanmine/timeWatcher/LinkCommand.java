package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public LinkCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("bot")) {
            player.sendMessage("§cИспользование: §f/link bot");
            return true;
        }

        if (!timeManager.hasPendingLink(player.getName())) {
            player.sendMessage("§cНет ожидающих привязок!");
            player.sendMessage("§7Сначала используй §f/link §7в Discord боте.");
            return true;
        }

        boolean success = timeManager.confirmLink(player);
        if (success) {
            player.sendMessage("§a✔ Discord аккаунт успешно привязан!");
        } else {
            player.sendMessage("§cОшибка привязки, попробуй снова.");
        }

        return true;
    }
}