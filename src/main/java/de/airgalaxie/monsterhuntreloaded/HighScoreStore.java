package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class HighScoreStore {
    private final MonsterHuntPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public HighScoreStore(MonsterHuntPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "highscores.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public int get(UUID playerId) {
        return data.getInt("players." + playerId + ".score", 0);
    }

    public void update(UUID playerId, String name, int score) {
        if (score <= get(playerId)) return;
        String path = "players." + playerId;
        data.set(path + ".name", name);
        data.set(path + ".score", score);
    }

    public Map<String, Integer> top(int limit) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!data.isConfigurationSection("players")) return result;
        data.getConfigurationSection("players").getKeys(false).stream()
                .map(key -> Map.entry(data.getString("players." + key + ".name", key),
                        data.getInt("players." + key + ".score")))
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(Math.max(1, Math.min(limit, 100)))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save highscores.yml", exception);
        }
    }
}
