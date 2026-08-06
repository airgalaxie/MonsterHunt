package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ReconnectStore {
    private final MonsterHuntPlugin plugin;
    private final DatabaseStorage storage;

    public ReconnectStore(MonsterHuntPlugin plugin, DatabaseStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void queueReturn(UUID playerId, Location location) { storage.queueReturn(playerId, location); }
    public void queueReward(UUID playerId, int place) { storage.queueReward(playerId, place); }

    public void consume(Player player, RewardService rewards) {
        for (DatabaseStorage.PendingAction action : storage.pendingActions(player.getUniqueId())) {
            boolean completed = false;
            if ("PLACE_REWARD".equals(action.type()) && action.rewardPlace() > 0) {
                rewards.givePlace(player, action.rewardPlace());
                completed = true;
            } else if ("RETURN_TELEPORT".equals(action.type()) && action.worldId() != null) {
                World world = plugin.getServer().getWorld(action.worldId());
                if (world != null) {
                    player.teleportAsync(new Location(world, action.x(), action.y(), action.z(), action.yaw(), action.pitch()))
                            .thenAccept(success -> { if (success) storage.completeAction(action.id()); });
                }
            }
            if (completed) storage.completeAction(action.id());
        }
    }
}
