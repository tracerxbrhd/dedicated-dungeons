package dev.uapi.dungeons.level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public enum EffectiveLevelStrategy {
    INITIATOR,
    MAXIMUM,
    MINIMUM,
    AVERAGE,
    MEDIAN;

    public int combine(List<Integer> levels) {
        if (levels.isEmpty()) throw new IllegalArgumentException("at least one participant level is required");
        List<Integer> safe = levels.stream().map(value -> Math.max(0, value)).toList();
        return switch (this) {
            case INITIATOR -> safe.getFirst();
            case MAXIMUM -> safe.stream().mapToInt(Integer::intValue).max().orElseThrow();
            case MINIMUM -> safe.stream().mapToInt(Integer::intValue).min().orElseThrow();
            case AVERAGE -> (int) Math.round(safe.stream().mapToInt(Integer::intValue).average().orElseThrow());
            case MEDIAN -> median(safe);
        };
    }

    public static EffectiveLevelStrategy byName(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return INITIATOR;
        }
    }

    private static int median(List<Integer> values) {
        ArrayList<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) return sorted.get(middle);
        return (int) Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
    }
}
