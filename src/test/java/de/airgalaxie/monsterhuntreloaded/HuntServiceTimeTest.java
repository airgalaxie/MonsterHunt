package de.airgalaxie.monsterhuntreloaded;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuntServiceTimeTest {
    @Test
    void convertsMinecraftClockToDayTicks() {
        assertEquals(13_000, HuntService.parseMinecraftTime("19:00"));
        assertEquals(23_000, HuntService.parseMinecraftTime("05:00"));
        assertEquals(0, HuntService.parseMinecraftTime("06:00"));
    }

    @Test
    void rejectsNonClockValues() {
        assertThrows(IllegalArgumentException.class, () -> HuntService.parseMinecraftTime("13000"));
        assertThrows(IllegalArgumentException.class, () -> HuntService.parseMinecraftTime("24:00"));
    }
}
