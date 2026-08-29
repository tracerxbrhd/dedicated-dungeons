package dev.uapi.dungeons.network;

import dev.uapi.dungeons.api.DungeonDeployment;
import dev.uapi.dungeons.api.DungeonDeploymentReadiness;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bounded codec for the immutable deployment projection; no provider-internal model crosses the wire. */
public final class DungeonDeploymentWireCodec {
    public static final int MAXIMUM_PARTICIPANTS = 256;

    private DungeonDeploymentWireCodec() {
    }

    public static void encode(RegistryFriendlyByteBuf buffer, DungeonDeployment deployment) {
        buffer.writeUUID(deployment.ownerId());
        writeOptionalUuid(buffer, deployment.groupId());
        if (deployment.participants().size() > MAXIMUM_PARTICIPANTS)
            throw new IllegalArgumentException("deployment participant count exceeds wire bound");
        buffer.writeVarInt(deployment.participants().size());
        deployment.participants().forEach(buffer::writeUUID);
        writeOptionalUuid(buffer, deployment.readyCheckId());
        buffer.writeEnum(deployment.readiness());
        buffer.writeVarLong(deployment.revision());
    }

    public static DungeonDeployment decode(RegistryFriendlyByteBuf buffer) {
        UUID ownerId = buffer.readUUID();
        Optional<UUID> groupId = readOptionalUuid(buffer);
        int size = buffer.readVarInt();
        if (size < 1 || size > MAXIMUM_PARTICIPANTS)
            throw new IllegalArgumentException("invalid deployment participant count: " + size);
        Set<UUID> participants = new LinkedHashSet<>();
        for (int index = 0; index < size; index++) participants.add(buffer.readUUID());
        if (participants.size() != size)
            throw new IllegalArgumentException("deployment payload contains duplicate participants");
        Optional<UUID> readyCheckId = readOptionalUuid(buffer);
        DungeonDeploymentReadiness readiness = buffer.readEnum(DungeonDeploymentReadiness.class);
        long revision = buffer.readVarLong();
        return new DungeonDeployment(ownerId, groupId, participants, readyCheckId, readiness, revision);
    }

    private static void writeOptionalUuid(RegistryFriendlyByteBuf buffer, Optional<UUID> value) {
        buffer.writeBoolean(value.isPresent());
        value.ifPresent(buffer::writeUUID);
    }

    private static Optional<UUID> readOptionalUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
    }
}
