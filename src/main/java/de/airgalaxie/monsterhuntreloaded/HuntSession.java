package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class HuntSession {
    private final UUID worldId;
    private HuntState state = HuntState.IDLE;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private Map<UUID, Integer> lastScores = Map.of();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private boolean manual;
    private boolean handledToday;
    private int skippedDays;

    public HuntSession(World world) {
        this.worldId = world.getUID();
    }

    public UUID worldId() { return worldId; }
    public HuntState state() { return state; }
    public void state(HuntState state) { this.state = state; }
    public Map<UUID, Integer> scores() { return scores; }
    public Map<UUID, Integer> lastScores() { return lastScores; }
    public Map<UUID, Location> returnLocations() { return returnLocations; }
    public boolean manual() { return manual; }
    public void manual(boolean manual) { this.manual = manual; }
    public boolean handledToday() { return handledToday; }
    public void handledToday(boolean handledToday) { this.handledToday = handledToday; }
    public int skippedDays() { return skippedDays; }
    public void skippedDays(int skippedDays) { this.skippedDays = skippedDays; }

    public boolean join(UUID playerId) {
        return scores.putIfAbsent(playerId, 0) == null;
    }

    public int addScore(UUID playerId, int points) {
        return scores.computeIfPresent(playerId, (ignored, old) -> old + points) == null
                ? -1 : scores.get(playerId);
    }

    public int penalize(UUID playerId, int percent) {
        Integer current = scores.get(playerId);
        if (current == null) return -1;
        int reduced = Math.max(0, (int) Math.round(current * (100 - percent) / 100.0));
        scores.put(playerId, reduced);
        return reduced;
    }

    public void reset() {
        lastScores = Map.copyOf(new LinkedHashMap<>(scores));
        scores.clear();
        returnLocations.clear();
        state = HuntState.IDLE;
        manual = false;
    }
}
