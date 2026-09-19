package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Giant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfiguredMonstersTest {
    private static final Pattern ENTITY_ENTRY = Pattern.compile("^    ([a-z0-9_]+): -?\\d+$", Pattern.MULTILINE);

    @Test
    void defaultPointsContainEveryActiveEnemyType() throws IOException {
        Set<String> expected = new TreeSet<>();
        Arrays.stream(EntityType.values())
                .filter(type -> type.getEntityClass() != null)
                .filter(type -> Enemy.class.isAssignableFrom(type.getEntityClass()))
                .filter(type -> !Giant.class.isAssignableFrom(type.getEntityClass()))
                .map(type -> type.getKey().getKey())
                .forEach(expected::add);

        Set<String> configured = configuredEntities();
        assertEquals(expected, configured,
                "Default points must match all Paper Enemy types with active vanilla combat AI");
    }

    private Set<String> configuredEntities() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream, "config.yml test resource");
            String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = ENTITY_ENTRY.matcher(config);
            Set<String> entities = new TreeSet<>();
            while (matcher.find()) entities.add(matcher.group(1));
            return entities;
        }
    }
}
