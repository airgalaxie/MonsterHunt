package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MonsterHuntPlugin extends JavaPlugin {
    private Messages messages;
    private HighScoreStore highScores;
    private HuntService hunts;
    private DatabaseStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(getConfig());
        try {
            storage = DatabaseStorage.open(this);
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "SQLite storage could not be started; disabling plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        highScores = new HighScoreStore(storage);
        hunts = new HuntService(this, messages, highScores, storage);

        ZoneSelector zoneSelector = new ZoneSelector(this);
        HuntCommands commands = new HuntCommands(this, zoneSelector);
        for (String name : List.of("hunt", "huntstatus", "huntscore", "huntstart", "huntstop", "huntzone", "hunttele")) {
            PluginCommand command = getCommand(name);
            if (command == null) throw new IllegalStateException("Command missing from plugin.yml: " + name);
            command.setExecutor(commands);
        }
        getServer().getPluginManager().registerEvents(new HuntListener(this), this);
        getServer().getPluginManager().registerEvents(zoneSelector, this);
        getServer().getScheduler().runTaskTimer(this, hunts::tick, 40L, 40L);
        scheduleBackups();
        getLogger().info("MonsterHuntReloaded enabled for Paper 26.2.");
    }

    @Override
    public void onDisable() {
        if (hunts != null) hunts.shutdown();
        if (storage != null) try { storage.close(); }
        catch (Exception exception) { getLogger().log(java.util.logging.Level.SEVERE, "Could not close SQLite storage", exception); }
    }

    public Messages messages() { return messages; }
    public HighScoreStore highScores() { return highScores; }
    public HuntService hunts() { return hunts; }

    private void scheduleBackups() {
        if (!getConfig().getBoolean("storage.sqlite.backup.enabled", true)) return;
        int hours = getConfig().getInt("storage.sqlite.backup.interval-hours", 24);
        int delayMinutes = getConfig().getInt("storage.sqlite.backup.initial-delay-minutes", 10);
        if (hours < 1 || hours > 8760) throw new IllegalArgumentException("backup.interval-hours must be between 1 and 8760");
        if (delayMinutes < 0 || delayMinutes > 1440) throw new IllegalArgumentException("backup.initial-delay-minutes must be between 0 and 1440");
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                var result = storage.backup();
                if (result != null) getLogger().info("SQLite backup created: " + result.getFileName());
            } catch (Exception exception) {
                getLogger().log(java.util.logging.Level.SEVERE, "Could not create SQLite backup", exception);
            }
        }, delayMinutes * 1200L, hours * 72_000L);
    }
}
