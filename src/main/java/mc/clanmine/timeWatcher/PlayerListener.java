package mc.clanmine.timeWatcher;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final TimeManager timeManager;

    public PlayerListener(TimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        timeManager.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        timeManager.saveSession(event.getPlayer());
    }
}