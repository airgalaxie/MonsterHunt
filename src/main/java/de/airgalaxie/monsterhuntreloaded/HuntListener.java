package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import static de.airgalaxie.monsterhuntreloaded.Messages.text;

public final class HuntListener implements Listener {
    private final MonsterHuntPlugin plugin;

    public HuntListener(MonsterHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.hunts().pause(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.hunts().resume(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player victim) {
            HuntSession session = plugin.hunts().session(victim.getWorld());
            if (session != null && session.state() == HuntState.RUNNING) {
                int penalty = Math.max(0, Math.min(100,
                        plugin.getConfig().getInt("hunt.death-penalty-percent", 30)));
                if (session.penalize(victim.getUniqueId(), penalty) >= 0 && penalty > 0) {
                    plugin.messages().send(victim, "death", text("percent", penalty));
                }
            }
            return;
        }

        if (!(dead instanceof Enemy)) return;
        if (dead instanceof Slime slime && slime.getSize() <= 1) return;

        Player killer = resolveKiller(dead);
        if (killer == null) return;
        HuntSession session = plugin.hunts().session(dead.getWorld());
        if (session == null || session.state() != HuntState.RUNNING) return;
        if (session.pausedPlayers().contains(killer.getUniqueId())) return;
        HuntZone zone = plugin.hunts().zone();
        if (zone != null && !zone.contains(dead.getLocation())) return;
        if (!isEligibleSpawn(dead.getLocation())) return;

        if (!session.scores().containsKey(killer.getUniqueId())) {
            if (plugin.getConfig().getBoolean("hunt.signup-enabled", true)) return;
            session.join(killer.getUniqueId());
        }

        String entityKey = dead.getType().getKey().getKey();
        String pointsPath = "points.entities." + entityKey;
        if (!plugin.getConfig().isInt(pointsPath)) return;
        int base = plugin.getConfig().getInt(pointsPath);
        int points = (int) Math.round(base * causeMultiplier(dead));
        if (points <= 0) return;

        int oldLead = session.scores().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int total = session.addScore(killer.getUniqueId(), points);
        if (total < 0) return;
        plugin.hunts().recordKill(session, killer, entityKey, points);
        plugin.messages().send(killer, "score", text("mob", entityKey), text("points", points), text("total", total));
        if (plugin.getConfig().getBoolean("hunt.announce-lead", true) && total > oldLead) {
            plugin.getServer().getOnlinePlayers().forEach(player -> plugin.messages().send(player, "lead",
                    text("player", killer.getName()), text("points", total)));
        }
    }

    private Player resolveKiller(LivingEntity entity) {
        Player direct = entity.getKiller();
        if (direct != null) return direct;
        if (!(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) return null;
        if (damage.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (damage.getDamager() instanceof Wolf wolf && wolf.getOwner() instanceof Player player) return player;
        return null;
    }

    private double causeMultiplier(LivingEntity entity) {
        if (!(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) return 1;
        if (damage.getDamager() instanceof Projectile) {
            return plugin.getConfig().getDouble("points.causes.projectile-multiplier", 0.5);
        }
        if (damage.getDamager() instanceof Wolf) {
            return plugin.getConfig().getDouble("points.causes.wolf-multiplier", 0.7);
        }
        return 1;
    }

    private boolean isEligibleSpawn(Location location) {
        if (!plugin.getConfig().getBoolean("hunt.only-count-surface-spawns", false)) return true;
        int surface = location.getWorld().getHighestBlockYAt(location);
        int tolerance = Math.max(0, plugin.getConfig().getInt("hunt.surface-tolerance-blocks", 4));
        return location.getBlockY() >= surface - tolerance;
    }
}
