package mc.clanmine.timeWatcher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TokensCommand implements CommandExecutor {

    private final TimeManager timeManager;

    public TokensCommand(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("timewatcher.admin")) {
            sender.sendMessage("§cНет доступа!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cИспользование: §f/tokens <ник> <add|remove|set> <количество>");
            return true;
        }

        String nick = args[0];
        String action = args[1].toLowerCase();
        double amount;

        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cНеверное количество!");
            return true;
        }

        boolean success = switch (action) {
            case "add" -> timeManager.modifyTokensByNick(nick, amount);
            case "remove" -> timeManager.modifyTokensByNick(nick, -amount);
            case "set" -> timeManager.setTokensByNick(nick, amount);
            default -> {
                sender.sendMessage("§cДействие: §fadd§c, §fremove§c, §fset");
                yield false;
            }
        };

        if (success) {
            sender.sendMessage("§a✔ Токены игрока §f" + nick + " §aобновлены!");
        } else {
            sender.sendMessage("§cИгрок §f" + nick + " §cне найден!");
        }

        return true;
    }
}