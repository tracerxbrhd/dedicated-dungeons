package dev.uapi.dungeons.network;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Versioned C2S request correlated to a server-issued deployment session. */
public record DungeonDeploymentActionPayload(
    int schemaVersion,
    UUID sessionId,
    long requestId,
    DungeonDeploymentAction action
) implements CustomPacketPayload {
    public static final Type<DungeonDeploymentActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "deployment_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonDeploymentActionPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public DungeonDeploymentActionPayload decode(RegistryFriendlyByteBuf buffer) {
                return new DungeonDeploymentActionPayload(buffer.readVarInt(), buffer.readUUID(),
                    buffer.readVarLong(), buffer.readEnum(DungeonDeploymentAction.class));
            }

            @Override public void encode(RegistryFriendlyByteBuf buffer, DungeonDeploymentActionPayload value) {
                buffer.writeVarInt(value.schemaVersion());
                buffer.writeUUID(value.sessionId());
                buffer.writeVarLong(value.requestId());
                buffer.writeEnum(value.action());
            }
        };

    public DungeonDeploymentActionPayload {
        if (schemaVersion < 0) throw new IllegalArgumentException("schemaVersion must not be negative");
        Objects.requireNonNull(sessionId, "sessionId");
        if (requestId < 1) throw new IllegalArgumentException("requestId must be positive");
        Objects.requireNonNull(action, "action");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
