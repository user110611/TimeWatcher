package mc.clanmine.timeWatcher;

import org.bukkit.plugin.java.JavaPlugin;

public class TimeWatcher extends JavaPlugin {

    private TimeManager timeManager;

    @Override
    public void onEnable() {
        this.timeManager = new TimeManager(this);
        timeManager.init();

        getServer().getPluginManager().registerEvents(new PlayerListener(timeManager), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        getCommand("mystats").setExecutor(new StatsCommand(timeManager));
        StatsModeCommand statsModeCommand = new StatsModeCommand(timeManager);
        getCommand("mystatsmode").setExecutor(statsModeCommand);
        getCommand("mystatsmode").setTabCompleter(statsModeCommand);
        getCommand("link").setExecutor(new LinkCommand(timeManager));
        getCommand("resetall").setExecutor(new ResetAllCommand(timeManager));
        getCommand("statsbot").setExecutor(new StatsBotCommand(timeManager));

        // Каждые 30 секунд сохраняем время в БД
        getServer().getScheduler().runTaskTimer(this, () -> {
            getServer().getOnlinePlayers().forEach(p -> {
                timeManager.saveSession(p);
                timeManager.onJoin(p);
            });
        }, 20L * 30, 20L * 30);

        // Каждые 30 минут +0.25 токена
        getServer().getScheduler().runTaskTimer(this, () -> {
            getServer().getOnlinePlayers().forEach(p -> {
                timeManager.addTokens(p, 0.25);
                p.sendMessage("§a+0.25 §7токена за игру на сервере!");
            });
        }, 20L * 60 * 30, 20L * 60 * 30);

        getLogger().info("TimeWatcher включён!");
    }

    @Override
    public void onDisable() {
        getServer().getOnlinePlayers().forEach(p -> timeManager.saveSession(p));
        timeManager.close();
        getLogger().info("TimeWatcher выключён!");
    }

    public TimeManager getTimeManager() {
        return timeManager;
    }
}