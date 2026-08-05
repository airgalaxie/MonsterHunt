package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MonsterHuntPlugin extends JavaPlugin {
    private Messages messages;
    private HighScoreStore highScores;
    private HuntService hunts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(getConfig());
        highScores = new HighScoreStore(this);
        hunts = new HuntService(this, messages, highScores);

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
        getLogger().info("MonsterHuntReloaded enabled for Paper 26.2.");
    }

    @Override
    public void onDisable() {
        if (hunts != null) hunts.shutdown();
    }

    public Messages messages() { return messages; }
    public HighScoreStore highScores() { return highScores; }
    public HuntService hunts() { return hunts; }
}
