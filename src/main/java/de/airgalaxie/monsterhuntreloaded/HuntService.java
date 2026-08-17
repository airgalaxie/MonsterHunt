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
    private final DatabaseStorage storage;
    private final Map<UUID, HuntSession> sessions = new HashMap<>();
    private HuntZone zone;
    private int scheduledStart;
    private int scheduledEnd;

    public HuntService(MonsterHuntPlugin plugin, Messages messages, HighScoreStore highScores, DatabaseStorage storage) {
        this.plugin = plugin;
        this.messages = messages;
        this.highScores = highScores;
        this.storage = storage;
        this.rewards = new RewardService(plugin);
        this.reconnects = new ReconnectStore(plugin, storage);
        reload();
    }

    public void reload() {
        zone = HuntZone.load(plugin.getConfig());
        scheduledStart = parseMinecraftTime(plugin.getConfig().getString("schedule.start-time", "19:00"));
        scheduledEnd = parseMinecraftTime(plugin.getConfig().getString("schedule.end-time", "05:00"));
        if (scheduledStart == scheduledEnd) {
            throw new IllegalArgumentException("schedule.start-time and schedule.end-time must be different");
        }
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
        int signupTicks = plugin.getConfig().getInt("schedule.signup-minutes", 1) * 1200;
        int signupStart = Math.floorMod(scheduledStart - signupTicks, 24000);
        for (HuntSession session : sessions.values()) {
            World world = Bukkit.getWorld(session.worldId());
            if (world == null || session.manual()) continue;
            int time = (int) world.getTime();
            if (session.state() == HuntState.IDLE && !session.handledToday()
                    && plugin.getConfig().getBoolean("hunt.signup-enabled", true)
                    && inRange(time, signupStart, scheduledStart)) {
                openSignup(session, world);
            }
            if ((session.state() == HuntState.SIGNUP
                    || (session.state() == HuntState.IDLE && !session.handledToday()))
                    && inRange(time, scheduledStart, scheduledEnd)) {
                autoStart(session, world);
            }
            if (session.state() == HuntState.RUNNING && !inRange(time, scheduledStart, scheduledEnd)) stop(session, world, true);
            if (!inRange(time, signupStart, scheduledEnd)) session.handledToday(false);
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
        session.databaseId(storage.startHunt(world, manual));
        broadcast("started", text("world", world.getName()));
        return true;
    }

    public void stop(HuntSession session, World world, boolean rewardPlayers) {
        if (session.state() == HuntState.IDLE) return;
        if (rewardPlayers && session.state() == HuntState.RUNNING) {
            Map<UUID, Integer> offlineRewards = new HashMap<>();
            Map<UUID, Integer> placements = new HashMap<>();
            Map<UUID, String> names = new HashMap<>();
            rewards.reward(session.scores()).forEach(result -> {
                String name = Bukkit.getOfflinePlayer(result.playerId()).getName();
                names.put(result.playerId(), name == null ? result.playerId().toString() : name);
                placements.put(result.playerId(), result.place());
                if (Bukkit.getPlayer(result.playerId()) == null) {
                    offlineRewards.put(result.playerId(), result.place());
                }
            });
            for (Map.Entry<UUID, Integer> entry : session.scores().entrySet()) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                names.putIfAbsent(entry.getKey(), name == null ? entry.getKey().toString() : name);
            }
            Map<UUID, Location> pendingReturns = new HashMap<>();
            session.returnLocations().forEach((playerId, location) -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) pendingReturns.put(playerId, location);
            });
            storage.completeHunt(session.databaseId(), session.scores(), placements, names,
                    offlineRewards, pendingReturns, true);
        } else if (session.state() == HuntState.RUNNING && session.databaseId() > 0) {
            Map<UUID, String> names = new HashMap<>();
            session.scores().keySet().forEach(playerId -> {
                String name = Bukkit.getOfflinePlayer(playerId).getName();
                names.put(playerId, name == null ? playerId.toString() : name);
            });
            Map<UUID, Location> pendingReturns = new HashMap<>();
            session.returnLocations().forEach((playerId, location) -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) pendingReturns.put(playerId, location);
            });
            storage.completeHunt(session.databaseId(), session.scores(), Map.of(), names,
                    Map.of(), pendingReturns, false);
        }
        session.returnLocations().forEach((playerId, location) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) player.teleportAsync(location);
            else if (!rewardPlayers) reconnects.queueReturn(playerId, location);
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

    public void recordKill(HuntSession session, Player player, String entityKey, int points) {
        storage.recordKill(session.databaseId(), player.getUniqueId(), player.getName(), entityKey, points);
    }

    private void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        Bukkit.getOnlinePlayers().forEach(player -> messages.send(player, key, tags));
        if (plugin.getConfig().getBoolean("messages.send-to-console", false)) {
            messages.send(Bukkit.getConsoleSender(), key, tags);
        }
    }

    private static boolean inRange(int time, int start, int end) {
        return start <= end ? time >= start && time < end : time >= start || time < end;
    }

    static int parseMinecraftTime(String value) {
        if (value == null || !value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("Minecraft time must use quoted HH:mm format, for example '19:00': " + value);
        }
        int hour = Integer.parseInt(value.substring(0, 2));
        int minute = Integer.parseInt(value.substring(3, 5));
        return Math.floorMod((int) Math.round(((hour * 60 + minute) - 360) * (1000.0 / 60)), 24000);
    }
}
