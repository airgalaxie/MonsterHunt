package de.airgalaxie.monsterhuntreloaded;

import java.util.Map;
import java.util.UUID;

public final class HighScoreStore {
    private final DatabaseStorage storage;

    public HighScoreStore(DatabaseStorage storage) {
        this.storage = storage;
    }

    public int get(UUID playerId) {
        return storage.highScore(playerId);
    }

    public Map<String, Integer> top(int limit) {
        return storage.top(limit);
    }
}
