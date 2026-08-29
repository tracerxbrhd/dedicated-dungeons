package dev.uapi.dungeons.network;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.api.DungeonDeployment;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Versioned S2C result containing the authoritative post-action deployment snapshot. */
public record DungeonDeploymentStatePayload(
    int schemaVersion,
    UUID sessionId,
    long requestId,
    DungeonDeploymentResponseStatus status,
    DungeonDeployment deployment
) implements CustomPacketPayload {
    public static final Type<DungeonDeploymentStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "deployment_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonDeploymentStatePayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public DungeonDeploymentStatePayload decode(RegistryFriendlyByteBuf buffer) {
                return new DungeonDeploymentStatePayload(buffer.readVarInt(), buffer.readUUID(),
                    buffer.readVarLong(), buffer.readEnum(DungeonDeploymentResponseStatus.class),
                    DungeonDeploymentWireCodec.decode(buffer));
            }

            @Override public void encode(RegistryFriendlyByteBuf buffer, DungeonDeploymentStatePayload value) {
                buffer.writeVarInt(value.schemaVersion());
                buffer.writeUUID(value.sessionId());
                buffer.writeVarLong(value.requestId());
                buffer.writeEnum(value.status());
                DungeonDeploymentWireCodec.encode(buffer, value.deployment());
            }
        };

    public DungeonDeploymentStatePayload {
        if (schemaVersion < 0) throw new IllegalArgumentException("schemaVersion must not be negative");
        Objects.requireNonNull(sessionId, "sessionId");
        if (requestId < 0) throw new IllegalArgumentException("requestId must not be negative");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(deployment, "deployment");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
