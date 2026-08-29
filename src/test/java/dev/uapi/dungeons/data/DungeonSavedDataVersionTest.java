package dev.uapi.dungeons.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class DungeonSavedDataVersionTest {
    @Test
    void acceptsOnlyTheExactCurrentVersion() {
        CompoundTag current = new CompoundTag();
        current.putInt(DungeonSavedData.DATA_VERSION_KEY, DungeonSavedData.DATA_VERSION);
        assertDoesNotThrow(() -> DungeonSavedData.requireCurrentDataVersion(current));

        CompoundTag older = new CompoundTag();
        older.putInt(DungeonSavedData.DATA_VERSION_KEY, DungeonSavedData.DATA_VERSION - 1);
        assertThrows(IllegalStateException.class,
            () -> DungeonSavedData.requireCurrentDataVersion(older));

        CompoundTag newer = new CompoundTag();
        newer.putInt(DungeonSavedData.DATA_VERSION_KEY, DungeonSavedData.DATA_VERSION + 1);
        assertThrows(IllegalStateException.class,
            () -> DungeonSavedData.requireCurrentDataVersion(newer));

        assertThrows(IllegalStateException.class,
            () -> DungeonSavedData.requireCurrentDataVersion(new CompoundTag()));
    }
}
