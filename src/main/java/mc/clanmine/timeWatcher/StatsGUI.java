package mc.clanmine.timeWatcher;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class StatsGUI {

    public static void open(Player player, TimeManager timeManager) {
        long totalSeconds = timeManager.getSeconds(player);
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        double tokens = timeManager.getTokens(player);

        String title = "Статистика " + player.getName();
        Inventory inv = Bukkit.createInventory(null, 27, title);

        fillBackground(inv);

        // Голова игрока — слот 4
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(player);
        skullMeta.setDisplayName("§e" + player.getName());
        skullMeta.setLore(List.of(
                "§7Всего: §e" + hours + "ч. " + minutes + "м. " + seconds + "с.",
                "§7Токены: " + (tokens < 0 ? "§c" : "§b") + String.format("%.2f", tokens)
        ));
        skull.setItemMeta(skullMeta);
        inv.setItem(4, skull);

        // Золотой блок — часы — слот 10
        ItemStack hoursItem = new ItemStack(Material.GOLD_BLOCK, (int) Math.min(Math.max(hours, 1), 127));
        ItemMeta hoursMeta = hoursItem.getItemMeta();
        hoursMeta.setDisplayName("§6Часы");
        hoursMeta.setLore(List.of(hours > 127
                ? "§7Точно: §e" + hours + " ч."
                : "§7Время на сервере: §e" + hours + " ч."));
        hoursItem.setItemMeta(hoursMeta);
        inv.setItem(10, hoursItem);

        // Золотой слиток — минуты — слот 12
        ItemStack minutesItem = new ItemStack(Material.GOLD_INGOT, (int) Math.max(minutes, 1));
        ItemMeta minutesMeta = minutesItem.getItemMeta();
        minutesMeta.setDisplayName("§eМинуты");
        minutesMeta.setLore(List.of("§7" + minutes + " мин."));
        minutesItem.setItemMeta(minutesMeta);
        inv.setItem(12, minutesItem);

        // Самородок — секунды — слот 14
        ItemStack secondsItem = new ItemStack(Material.GOLD_NUGGET, (int) Math.max(seconds, 1));
        ItemMeta secondsMeta = secondsItem.getItemMeta();
        secondsMeta.setDisplayName("§fСекунды");
        secondsMeta.setLore(List.of("§7" + seconds + " сек."));
        secondsItem.setItemMeta(secondsMeta);
        inv.setItem(14, secondsItem);

        // Алмаз — токены — слот 16
        String tokenColor = tokens < 0 ? "§c" : "§b";
        ItemStack tokenItem = new ItemStack(Material.DIAMOND);
        ItemMeta tokenMeta = tokenItem.getItemMeta();
        tokenMeta.setDisplayName(tokenColor + String.format("%.2f", tokens) + " токенов");
        tokenMeta.setLore(List.of("§7Ваш баланс токенов"));
        tokenItem.setItemMeta(tokenMeta);
        inv.setItem(16, tokenItem);

        player.openInventory(inv);
    }

    private static void fillBackground(Inventory inv) {
        ItemStack red   = makePane(Material.RED_STAINED_GLASS_PANE);
        ItemStack black = makePane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, (i % 2 == 0) ? red : black);
        }
    }

    private static ItemStack makePane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}