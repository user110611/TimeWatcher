package mc.clanmine.timeWatcher;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("Статистика ")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith("Статистика ")) {
            event.setCancelled(true);
        }
    }
}