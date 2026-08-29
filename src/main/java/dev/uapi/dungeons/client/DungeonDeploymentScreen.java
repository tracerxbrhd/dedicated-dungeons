package dev.uapi.dungeons.client;

import dev.uapi.client.ui.components.UIButton;
import dev.uapi.client.ui.components.UILabel;
import dev.uapi.client.ui.components.UIPanel;
import dev.uapi.client.ui.components.UIProgressBar;
import dev.uapi.client.ui.core.UIContainer;
import dev.uapi.client.ui.core.UIScreen;
import dev.uapi.client.ui.theme.UITheme;
import dev.uapi.client.ui.theme.UITheme.ColorToken;
import dev.uapi.client.ui.theme.UIThemes;
import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.api.DungeonDeployment;
import dev.uapi.dungeons.api.DungeonDeploymentReadiness;
import dev.uapi.dungeons.content.DungeonContentRegistry;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Retained U-API deployment screen foundation.
 *
 * <p>The screen accepts immutable deployment snapshots and transport callbacks. It knows nothing
 * about provider-owned party storage or any optional consumer mod.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class DungeonDeploymentScreen extends UIScreen {
    public interface Actions {
        void refresh();

        void startReadyCheck();

        void deploy();

        default void tick() {
        }

        default void closed() {
        }
    }

    private static final UITheme THEME = UIThemes.ARCANE;
    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_HEIGHT = 218;

    private DungeonDeployment deployment;
    private final DifficultyRank rank;
    private final ResourceLocation archetypeId;
    private final Actions actions;
    private UIPanel panel;
    private UILabel heading;
    private UILabel dungeon;
    private UILabel difficulty;
    private UILabel mode;
    private UILabel participants;
    private UILabel group;
    private UILabel feedback;
    private UIProgressBar readiness;
    private UIButton refresh;
    private UIButton readyCheck;
    private UIButton deploy;
    private boolean pending;
    private boolean sessionActive = true;

    public DungeonDeploymentScreen(DungeonDeployment initial, Actions actions) {
        this(DifficultyRank.E, DungeonContentRegistry.DEFAULT_DUNGEON, initial, actions);
    }

    public DungeonDeploymentScreen(DifficultyRank rank, ResourceLocation archetypeId,
                                   DungeonDeployment initial, Actions actions) {
        super(Component.translatable("screen.dedicated_dungeons.deployment.title"));
        this.rank = Objects.requireNonNull(rank, "rank");
        this.archetypeId = Objects.requireNonNull(archetypeId, "archetypeId");
        this.deployment = Objects.requireNonNull(initial, "initial");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /** Applies a newer server snapshot without rebuilding retained components. */
    public void update(DungeonDeployment snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.ownerId().equals(deployment.ownerId()))
            throw new IllegalArgumentException("deployment owner cannot change during a screen lifetime");
        // Group/check replacement can legitimately lower the provider-local revision. Networked
        // callers already order replies by request id; only reject a regression of the same source.
        if (snapshot.revision() < deployment.revision()
            && snapshot.groupId().equals(deployment.groupId())
            && snapshot.readyCheckId().equals(deployment.readyCheckId())) return;
        deployment = snapshot;
        updateComponents();
    }

    public DungeonDeployment snapshot() {
        return deployment;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
        updateComponents();
    }

    public void setSessionActive(boolean sessionActive) {
        this.sessionActive = sessionActive;
        updateComponents();
    }

    public void showStatus(Component message, boolean error) {
        if (feedback == null) return;
        feedback.setText(Objects.requireNonNull(message, "message"));
        feedback.setColor(error ? ColorToken.ACCENT_DANGER : ColorToken.ACCENT_SUCCESS);
    }

    @Override
    protected void buildUi(UIContainer root) {
        root.setTheme(THEME);
        panel = root.add(new UIPanel());
        heading = root.add(new UILabel(title, ColorToken.TEXT_PRIMARY));
        heading.setShadow(true);
        dungeon = root.add(new UILabel(Component.empty(), ColorToken.TEXT_SECONDARY));
        difficulty = root.add(new UILabel(Component.empty(), ColorToken.TEXT_SECONDARY));
        mode = root.add(new UILabel(Component.empty(), ColorToken.TEXT_SECONDARY));
        participants = root.add(new UILabel(Component.empty(), ColorToken.TEXT_SECONDARY));
        group = root.add(new UILabel(Component.empty(), ColorToken.TEXT_MUTED));
        readiness = root.add(new UIProgressBar(0, Component.empty()));
        feedback = root.add(new UILabel(Component.empty(), ColorToken.ACCENT_SUCCESS));
        refresh = root.add(new UIButton(Component.translatable(
            "screen.dedicated_dungeons.deployment.refresh"), actions::refresh));
        readyCheck = root.add(new UIButton(Component.translatable(
            "screen.dedicated_dungeons.deployment.ready_check"), actions::startReadyCheck));
        deploy = root.add(new UIButton(Component.translatable(
            "screen.dedicated_dungeons.deployment.deploy"), actions::deploy));
        updateComponents();
    }

    @Override
    protected void layoutUi(UIContainer root) {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        panel.setBounds(left, top, PANEL_WIDTH, PANEL_HEIGHT);
        heading.setBounds(left + 14, top + 13, PANEL_WIDTH - 28, 12);
        dungeon.setBounds(left + 14, top + 35, PANEL_WIDTH - 28, 12);
        difficulty.setBounds(left + 14, top + 52, PANEL_WIDTH - 28, 12);
        mode.setBounds(left + 14, top + 72, PANEL_WIDTH - 28, 12);
        participants.setBounds(left + 14, top + 89, PANEL_WIDTH - 28, 12);
        group.setBounds(left + 14, top + 106, PANEL_WIDTH - 28, 12);
        readiness.setBounds(left + 14, top + 128, PANEL_WIDTH - 28, 18);
        feedback.setBounds(left + 14, top + 157, PANEL_WIDTH - 28, 12);
        int buttonTop = top + 182;
        int gap = 6;
        int buttonWidth = (PANEL_WIDTH - 28 - gap * 2) / 3;
        refresh.setBounds(left + 14, buttonTop, buttonWidth, 22);
        readyCheck.setBounds(left + 14 + buttonWidth + gap, buttonTop, buttonWidth, 22);
        deploy.setBounds(left + 14 + (buttonWidth + gap) * 2, buttonTop, buttonWidth, 22);
    }

    @Override
    protected void tickScreen() {
        actions.tick();
    }

    private void updateComponents() {
        if (mode == null) return;
        dungeon.setText(Component.translatable("screen.dedicated_dungeons.deployment.dungeon",
            archetypeId.toString()));
        difficulty.setText(Component.translatable("screen.dedicated_dungeons.deployment.difficulty",
            rank.displayName()));
        mode.setText(Component.translatable(deployment.partyBacked()
            ? "screen.dedicated_dungeons.deployment.mode.party"
            : "screen.dedicated_dungeons.deployment.mode.solo"));
        participants.setText(Component.translatable("screen.dedicated_dungeons.deployment.participants",
            deployment.participants().size()));
        group.setText(deployment.groupId()
            .<Component>map(id -> Component.translatable("screen.dedicated_dungeons.deployment.group",
                id.toString().substring(0, 8)))
            .orElseGet(() -> Component.translatable("screen.dedicated_dungeons.deployment.no_group")));
        readiness.setProgress(progress(deployment.readiness()));
        readiness.setLabel(Component.translatable(statusKey(deployment.readiness())));
        refresh.setEnabled(sessionActive && !pending);
        readyCheck.setEnabled(sessionActive && !pending && deployment.partyBacked()
            && canStartReadyCheck(deployment.readiness()));
        deploy.setEnabled(sessionActive && !pending && deployment.canDeploy());
    }

    @Override
    protected void removedScreen() {
        actions.closed();
    }

    private static boolean canStartReadyCheck(DungeonDeploymentReadiness state) {
        return switch (state) {
            case CHECKING, ALL_READY -> false;
            default -> true;
        };
    }

    private static double progress(DungeonDeploymentReadiness state) {
        return switch (state) {
            case SOLO_READY, ALL_READY -> 1.0;
            case CHECKING -> 0.5;
            default -> 0.0;
        };
    }

    private static String statusKey(DungeonDeploymentReadiness state) {
        return "screen.dedicated_dungeons.deployment.status." + state.name().toLowerCase(java.util.Locale.ROOT);
    }
}
