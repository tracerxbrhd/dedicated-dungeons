package dev.underworld.dungeons.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonSavedData extends SavedData {
    private static final Factory<DungeonSavedData> FACTORY = new Factory<>(DungeonSavedData::new, DungeonSavedData::load);
    private final Map<UUID, PortalRecord> portals = new LinkedHashMap<>();
    private final Map<UUID, DungeonSession> sessions = new LinkedHashMap<>();
    private final Map<String, Long> randomCooldowns = new LinkedHashMap<>();

    public static DungeonSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "dedicated_dungeons");
    }
    public Map<UUID, PortalRecord> portals() { return portals; }
    public Map<UUID, DungeonSession> sessions() { return sessions; }
    public Map<String, Long> randomCooldowns() { return randomCooldowns; }
    public void changed() { setDirty(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag portalList = new ListTag(); portals.values().forEach(value -> portalList.add(value.save()));
        ListTag sessionList = new ListTag(); sessions.values().forEach(value -> sessionList.add(value.save()));
        tag.put("portals", portalList); tag.put("sessions", sessionList);
        CompoundTag cooldowns = new CompoundTag();
        randomCooldowns.forEach(cooldowns::putLong);
        tag.put("randomCooldowns", cooldowns);
        return tag;
    }
    private static DungeonSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonSavedData data = new DungeonSavedData();
        for (Tag value : tag.getList("portals", Tag.TAG_COMPOUND)) {
            PortalRecord portal = PortalRecord.load((CompoundTag) value); data.portals.put(portal.id(), portal);
        }
        for (Tag value : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            DungeonSession session = DungeonSession.load((CompoundTag) value); data.sessions.put(session.instanceId(), session);
        }
        CompoundTag cooldowns = tag.getCompound("randomCooldowns");
        for (String key : cooldowns.getAllKeys()) data.randomCooldowns.put(key, cooldowns.getLong(key));
        return data;
    }
}
