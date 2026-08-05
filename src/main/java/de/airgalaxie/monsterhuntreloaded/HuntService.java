package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.airgalaxie.monsterhuntreloaded.Messages.text;

public final class HuntService {
    private final MonsterHuntPlugin plugin;
    private final Messages messages;
    private final HighScoreStore highScores;
    private final RewardService rewards;
    private final ReconnectStore reconnects;
    private final Map<UUID, HuntSession> sessions = new HashMap<>();
    private HuntZone zone;

    public HuntService(MonsterHuntPlugin plugin, Messages messages, HighScoreStore highScores) {
        this.plugin = plugin;
        this.messages = messages;
        this.highScores = highScores;
        this.rewards = new RewardService(plugin);
        this.reconnects = new ReconnectStore(plugin);
        reload();
    }

    public void reload() {
        zone = HuntZone.load(plugin.getConfig());
        sessions.clear();
        List<String> enabled = plugin.getConfig().getStringList("enabled-worlds");
        Collection<World> worlds = enabled.isEmpty() ? Bukkit.getWorlds()
                : enabled.stream().map(Bukkit::getWorld).filter(java.util.Objects::nonNull).toList();
        worlds.forEach(world -> sessions.put(world.getUID(), new HuntSession(world)));
        if (zone != null) {
            World world = Bukkit.getWorld(zone.worldId());
            if (world != null) sessions.putIfAbsent(world.getUID(), new HuntSession(world));
        }
    }

    public HuntSession session(World world) {
        if (zone != null) return sessions.get(zone.worldId());
        return sessions.get(world.getUID());
    }

    public Collection<HuntSession> sessions() { return sessions.values(); }
    public HuntZone zone() { return zone; }

    public void tick() {
        int start = plugin.getConfig().getInt("schedule.start-time", 13000);
        int end = plugin.getConfig().getInt("schedule.end-time", 23600);
        int signupTicks = plugin.getConfig().getInt("schedule.signup-minutes", 5) * 1200;
        int signupStart = Math.floorMod(start - signupTicks, 24000);
        for (HuntSession session : sessions.values()) {
            World world = Bukkit.getWorld(session.worldId());
            if (world == null || session.manual()) continue;
            int time = (int) world.getTime();
            if (session.state() == HuntState.IDLE && !session.handledToday()
                    && plugin.getConfig().getBoolean("hunt.signup-enabled", true)
                    && inRange(time, signupStart, start)) {
                openSignup(session, world);
            }
            if ((session.state() == HuntState.SIGNUP
                    || (session.state() == HuntState.IDLE && !session.handledToday()))
                    && inRange(time, start, end)) {
                autoStart(session, world);
            }
            if (session.state() == HuntState.RUNNING && !inRange(time, start, end)) stop(session, world, true);
            if (!inRange(time, signupStart, end)) session.handledToday(false);
        }
    }

    public void openSignup(HuntSession session, World world) {
        session.state(HuntState.SIGNUP);
        broadcast("signup-open", text("world", world.getName()));
    }

    private void autoStart(HuntSession session, World world) {
        int skipDays = plugin.getConfig().getInt("schedule.skip-days", 0);
        if (session.skippedDays() < skipDays) {
            session.skippedDays(session.skippedDays() + 1);
            session.handledToday(true);
            return;
        }
        session.skippedDays(0);
        int chance = Math.max(0, Math.min(100, plugin.getConfig().getInt("schedule.start-chance-percent", 100)));
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= chance) {
            session.handledToday(true);
            return;
        }
        start(session, world, false);
    }

    public boolean start(HuntSession session, World world, boolean manual) {
        if (session.state() == HuntState.RUNNING) return false;
        int minimum = plugin.getConfig().getInt("hunt.minimum-players", 2);
        boolean signup = plugin.getConfig().getBoolean("hunt.signup-enabled", true);
        if (!manual && signup && session.scores().size() < minimum) {
            broadcast("not-enough-players");
            session.reset();
            session.handledToday(true);
            return false;
        }
        session.state(HuntState.RUNNING);
        session.manual(manual);
        session.handledToday(true);
        broadcast("started", text("world", world.getName()));
        return true;
    }

    public void stop(HuntSession session, World world, boolean rewardPlayers) {
        if (session.state() == HuntState.IDLE) return;
        if (rewardPlayers && session.state() == HuntState.RUNNING) {
            rewards.reward(session.scores()).forEach(result -> {
                String name = Bukkit.getOfflinePlayer(result.playerId()).getName();
                highScores.update(result.playerId(), name == null ? result.playerId().toString() : name, result.score());
                if (Bukkit.getPlayer(result.playerId()) == null) {
                    reconnects.queueReward(result.playerId(), result.place());
                }
            });
            for (Map.Entry<UUID, Integer> entry : session.scores().entrySet()) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                highScores.update(entry.getKey(), name == null ? entry.getKey().toString() : name, entry.getValue());
            }
            highScores.save();
        }
        session.returnLocations().forEach((playerId, location) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) player.teleportAsync(location);
            else reconnects.queueReturn(playerId, location);
        });
        session.reset();
        broadcast("stopped", text("world", world.getName()));
    }

    public boolean join(Player player) {
        HuntSession session = session(player.getWorld());
        if (session == null) return false;
        boolean canJoin = session.state() == HuntState.SIGNUP
                || (session.state() == HuntState.RUNNING
                && plugin.getConfig().getBoolean("hunt.allow-signup-after-start", false));
        if (!canJoin) {
            messages.send(player, "too-late");
            return false;
        }
        if (!session.join(player.getUniqueId())) {
            messages.send(player, "already-signed-up");
            return false;
        }
        messages.send(player, "signed-up", text("world", player.getWorld().getName()));
        return true;
    }

    public void shutdown() {
        for (HuntSession session : sessions.values()) {
            World world = Bukkit.getWorld(session.worldId());
            if (world != null) stop(session, world, false);
        }
        highScores.save();
        reconnects.save();
    }

    public void pause(Player player) {
        HuntSession session = session(player.getWorld());
        if (session != null && session.state() != HuntState.IDLE
                && session.scores().containsKey(player.getUniqueId())) {
            session.pausedPlayers().add(player.getUniqueId());
        }
    }

    public void resume(Player player) {
        reconnects.consume(player, rewards);
        HuntSession session = session(player.getWorld());
        if (session != null && session.pausedPlayers().remove(player.getUniqueId())) {
            player.sendRichMessage("<gold>[MonsterHunt]</gold> <green>Deine pausierte Jagd wird mit unverändertem Punktestand fortgesetzt.</green>");
        }
    }

    public void rememberTeleport(Player player, Location origin) {
        HuntSession session = session(player.getWorld());
        if (session != null) session.returnLocations().putIfAbsent(player.getUniqueId(), origin);
    }

    private void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        Bukkit.getOnlinePlayers().forEach(player -> messages.send(player, key, tags));
        messages.send(Bukkit.getConsoleSender(), key, tags);
    }

    private static boolean inRange(int time, int start, int end) {
        return start <= end ? time >= start && time < end : time >= start || time < end;
    }
}
