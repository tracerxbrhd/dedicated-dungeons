package dev.uapi.dungeons.network;

import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.api.DungeonDeployment;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Versioned S2C snapshot which creates a client deployment session. */
public record DungeonDeploymentOpenPayload(
    int schemaVersion,
    UUID sessionId,
    DifficultyRank rank,
    ResourceLocation archetypeId,
    DungeonDeployment deployment
) implements CustomPacketPayload {
    public static final Type<DungeonDeploymentOpenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "deployment_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonDeploymentOpenPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public DungeonDeploymentOpenPayload decode(RegistryFriendlyByteBuf buffer) {
                return new DungeonDeploymentOpenPayload(buffer.readVarInt(), buffer.readUUID(),
                    buffer.readEnum(DifficultyRank.class), buffer.readResourceLocation(),
                    DungeonDeploymentWireCodec.decode(buffer));
            }

            @Override public void encode(RegistryFriendlyByteBuf buffer, DungeonDeploymentOpenPayload value) {
                buffer.writeVarInt(value.schemaVersion());
                buffer.writeUUID(value.sessionId());
                buffer.writeEnum(value.rank());
                buffer.writeResourceLocation(value.archetypeId());
                DungeonDeploymentWireCodec.encode(buffer, value.deployment());
            }
        };

    public DungeonDeploymentOpenPayload {
        if (schemaVersion < 0) throw new IllegalArgumentException("schemaVersion must not be negative");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(archetypeId, "archetypeId");
        Objects.requireNonNull(deployment, "deployment");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
