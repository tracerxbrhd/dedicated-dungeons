package dev.uapi.dungeons.network;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DungeonDeploymentPayloadContractTest {
    @Test
    void rejectsNegativeSchemaAndNonPositiveRequestMetadata() {
        UUID session = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new DungeonDeploymentActionPayload(
            -1, session, 1, DungeonDeploymentAction.REFRESH));
        assertThrows(IllegalArgumentException.class, () -> new DungeonDeploymentActionPayload(
            1, session, 0, DungeonDeploymentAction.LAUNCH));
    }
}
