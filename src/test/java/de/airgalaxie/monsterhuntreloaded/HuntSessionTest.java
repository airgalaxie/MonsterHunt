package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HuntSessionTest {
    @Test
    void disqualificationClearsExistingScoreAndBlocksFurtherPoints() {
        HuntSession session = session();
        UUID playerId = UUID.randomUUID();
        session.join(playerId);
        assertEquals(20, session.addScore(playerId, 20));

        assertTrue(session.disqualify(playerId));
        assertEquals(0, session.scores().get(playerId));
        assertEquals(-1, session.addScore(playerId, 10));
        assertEquals(0, session.scores().get(playerId));
    }

    @Test
    void disqualificationBeforeAutoJoinStillBlocksPoints() {
        HuntSession session = session();
        UUID playerId = UUID.randomUUID();

        assertTrue(session.disqualify(playerId));
        assertTrue(session.join(playerId));
        assertEquals(-1, session.addScore(playerId, 10));
        assertEquals(0, session.scores().get(playerId));
        assertFalse(session.disqualify(playerId));
    }

    private static HuntSession session() {
        UUID worldId = UUID.randomUUID();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, arguments) -> method.getName().equals("getUID") ? worldId : defaultValue(method.getReturnType()));
        return new HuntSession(world);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new AssertionError(type);
    }
}
