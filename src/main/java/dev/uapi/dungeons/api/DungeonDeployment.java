package dev.uapi.dungeons.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable, UI-safe snapshot of the players targeted by one dungeon deployment. */
public record DungeonDeployment(
    UUID ownerId,
    Optional<UUID> groupId,
    Set<UUID> participants,
    Optional<UUID> readyCheckId,
    DungeonDeploymentReadiness readiness,
    long revision
) {
    public DungeonDeployment {
        Objects.requireNonNull(ownerId, "ownerId");
        groupId = Objects.requireNonNull(groupId, "groupId");
        readyCheckId = Objects.requireNonNull(readyCheckId, "readyCheckId");
        readiness = Objects.requireNonNull(readiness, "readiness");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");

        LinkedHashSet<UUID> copied = new LinkedHashSet<>();
        for (UUID participant : Objects.requireNonNull(participants, "participants"))
            copied.add(Objects.requireNonNull(participant, "participants must not contain null"));
        if (!copied.contains(ownerId)) throw new IllegalArgumentException("participants must contain ownerId");
        participants = Collections.unmodifiableSet(copied);

        if (groupId.isEmpty() && copied.size() != 1)
            throw new IllegalArgumentException("a solo deployment cannot contain additional participants");
        if (readiness == DungeonDeploymentReadiness.SOLO_READY && groupId.isPresent())
            throw new IllegalArgumentException("SOLO_READY cannot reference a social group");
        if (readyCheckId.isPresent() && groupId.isEmpty())
            throw new IllegalArgumentException("a solo deployment cannot reference a ready check");
    }

    public boolean partyBacked() {
        return groupId.isPresent();
    }

    public boolean canDeploy() {
        return readiness.canDeploy();
    }
}
