package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RewardService {
    private final MonsterHuntPlugin plugin;

    public RewardService(MonsterHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Result> reward(Map<UUID, Integer> scores) {
        List<Map.Entry<UUID, Integer>> ordered = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        List<Result> results = new ArrayList<>();
        int maximumPlaces = plugin.getConfig().getInt("rewards.winners", 3);
        int place = 0;
        int lastScore = Integer.MIN_VALUE;
        for (Map.Entry<UUID, Integer> entry : ordered) {
            if (entry.getValue() != lastScore) place++;
            if (place > maximumPlaces) break;
            lastScore = entry.getValue();
            int minimum = plugin.getConfig().getInt("rewards.places." + place + ".minimum-points", 1);
            if (entry.getValue() < minimum) continue;
            give(entry.getKey(), place);
            results.add(new Result(place, entry.getKey(), entry.getValue()));
        }
        return results;
    }

    private void give(UUID playerId, int place) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        String base = "rewards.places." + place;
        for (String specification : plugin.getConfig().getStringList(base + ".items")) {
            String[] parts = specification.trim().split("\\s+");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Unknown reward material: " + specification);
                continue;
            }
            int amount;
            try {
                amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Invalid reward amount: " + specification);
                continue;
            }
            if (amount < 1) continue;
            Map<Integer, ItemStack> leftovers = new HashMap<>(player.getInventory().addItem(new ItemStack(material, amount)));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        double money = plugin.getConfig().getDouble(base + ".money", 0);
        if (money > 0) depositViaVault(player, money);
    }

    private void depositViaVault(OfflinePlayer player, double amount) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null) return;
            Method getProvider = registration.getClass().getMethod("getProvider");
            Object economy = getProvider.invoke(registration);
            Method deposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            deposit.invoke(economy, player, amount);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Vault economy reward unavailable: " + exception.getMessage());
        }
    }

    public record Result(int place, UUID playerId, int score) { }
}
