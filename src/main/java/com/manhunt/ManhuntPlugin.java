package com.manhunt;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ManhuntPlugin extends JavaPlugin {

    private CompassTracker compassTracker;
    private WorldResetManager worldResetManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("compass.name", null);
        getConfig().set("compass.lore", null);
        getConfig().set("runners-luck.enabled", null);
        saveConfig();

        // start compass tracker first (updates every 20 ticks = 1 second)
        compassTracker = new CompassTracker(this);
        getServer().getPluginManager().registerEvents(compassTracker, this);
        long updateInterval = Math.max(1L, getConfig().getLong("compass.update-interval-ticks", 20L));
        getServer().getScheduler().runTaskTimer(this, compassTracker::updateCompasses, 0L, updateInterval);

        // register runner's luck after the tracker so it can identify current runners
        getServer().getPluginManager().registerEvents(new RunnersLuck(this, compassTracker), this);

        worldResetManager = new WorldResetManager(this);
        getServer().getPluginManager().registerEvents(worldResetManager, this);

        // register commands
        var manhuntCommand = Objects.requireNonNull(getCommand("manhunt"), "manhunt command missing from plugin.yml");
        manhuntCommand.setExecutor(new ManhuntCommand(this));
        manhuntCommand.setTabCompleter(new ManhuntTabCompleter());

        getLogger().info("manhunt plugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("manhunt plugin disabled");
    }

    public CompassTracker getCompassTracker() {
        return compassTracker;
    }

    public WorldResetManager getWorldResetManager() {
        return worldResetManager;
    }
}
