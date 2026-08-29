package dev.uapi.dungeons.level;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EffectiveLevelStrategyTest {
    @Test
    void combinesMultiplayerLevelsDeterministically() {
        var levels = List.of(30, 2, 10, 20);
        assertEquals(30, EffectiveLevelStrategy.INITIATOR.combine(levels));
        assertEquals(30, EffectiveLevelStrategy.MAXIMUM.combine(levels));
        assertEquals(2, EffectiveLevelStrategy.MINIMUM.combine(levels));
        assertEquals(16, EffectiveLevelStrategy.AVERAGE.combine(levels));
        assertEquals(15, EffectiveLevelStrategy.MEDIAN.combine(levels));
    }

    @Test
    void normalizesNegativeLevelsAndRequiresParticipants() {
        assertEquals(0, EffectiveLevelStrategy.MINIMUM.combine(List.of(-5, 10)));
        assertThrows(IllegalArgumentException.class, () -> EffectiveLevelStrategy.MEDIAN.combine(List.of()));
    }
}
