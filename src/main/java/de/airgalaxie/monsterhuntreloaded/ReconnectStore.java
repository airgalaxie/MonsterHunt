package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public final class ReconnectStore {
    private final MonsterHuntPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public ReconnectStore(MonsterHuntPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reconnect.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void queueReturn(UUID playerId, Location location) {
        data.set(path(playerId) + ".return-location", location);
        save();
    }

    public void queueReward(UUID playerId, int place) {
        data.set(path(playerId) + ".reward-place", place);
        save();
    }

    public void consume(Player player, RewardService rewards) {
        String path = path(player.getUniqueId());
        Location returnLocation = data.getLocation(path + ".return-location");
        int rewardPlace = data.getInt(path + ".reward-place", 0);
        if (returnLocation != null) player.teleportAsync(returnLocation);
        if (rewardPlace > 0) rewards.givePlace(player, rewardPlace);
        if (returnLocation != null || rewardPlace > 0) {
            data.set(path, null);
            save();
        }
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save reconnect.yml", exception);
        }
    }

    private static String path(UUID playerId) {
        return "players." + playerId;
    }
}
