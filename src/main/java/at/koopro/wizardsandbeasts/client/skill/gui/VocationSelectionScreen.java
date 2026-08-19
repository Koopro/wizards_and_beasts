package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.skill.state.ClientVocationCache;
import at.koopro.wizardsandbeasts.network.skill.VocationCommitC2SPayload;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Declares a Vocation from the skill screen.
 *
 * <p>Declaration existed only as {@code /wandb player vocation set primary <id>}, which is unreachable on a
 * world with cheats off — the whole specialization layer was invisible to a survival player.
 *
 * <p>Wears the {@code star_chart} skin, like the chart it opens from. It used to be vanilla widgets
 * on two flat {@code fill}ed rectangles, which was defensible while the screen behind it was flat
 * too and stopped being so the moment that one became night void and brass.
 */
public class VocationSelectionScreen extends Screen {

    private final @Nullable Screen parent;
    private final List<VocationDefinition> vocations = new ArrayList<>();
    private final List<ThemedButton> vocationButtons = new ArrayList<>();
    private @Nullable VocationDefinition focused;
    /**
     * Optimistic declaration: the id sent to the server whose {@code VocationDataSyncS2CPayload} has not
     * come back yet. Without it the highlight would only catch up on the <em>next</em> interaction, which
     * read in-game as "every button needs a double click".
     */
    private @Nullable Identifier pending;
    private GuiScaleHelper.Layout layout;

    public VocationSelectionScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.wizards_and_beasts.vocation.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        vocations.clear();
        vocations.addAll(VocationRegistry.all());
        vocationButtons.clear();

        layout = GuiScaleHelper.Layout.panel(width, height,
                WizardsAndBeastsUiTokens.SkillTree.PANEL_WIDTH, WizardsAndBeastsUiTokens.SkillTree.PANEL_HEIGHT);

        int buttonW = layout.s(150);
        int buttonH = layout.s(20);
        int x = layout.panelX() + layout.s(16);
        int y = layout.panelY() + layout.s(40);

        for (VocationDefinition vocation : vocations) {
            ThemedButton button = chartButton(x, y, buttonW, buttonH, vocation.displayName(),
                    () -> declare(vocation));
            addRenderableWidget(button);
            vocationButtons.add(button);
            y += buttonH + layout.s(4);
        }

        addRenderableWidget(chartButton(
                layout.panelX() + layout.panelW() - layout.s(16) - layout.s(80),
                layout.panelY() + layout.panelH() - layout.s(28), layout.s(80), buttonH,
                Component.translatable("gui.done"), this::onClose));

        Identifier active = activeVocation();
        focused = active != null ? VocationRegistry.get(active)
                : (vocations.isEmpty() ? null : vocations.get(0));
        refreshLabels();
    }

    private void declare(VocationDefinition vocation) {
        ClientPacketDistributor.sendToServer(new VocationCommitC2SPayload(vocation.id().toString()));
        focused = vocation;
        // Highlight immediately off the optimistic id; refreshLabels() hands back over to the cache
        // once the server's VocationDataSyncS2CPayload confirms it, so a rejected commit self-corrects.
        pending = vocation.id();
        refreshLabels();
    }

    /** The declaration to highlight: the un-acknowledged optimistic id, else the synced one. */
    private @Nullable Identifier activeVocation() {
        Optional<Identifier> synced = ClientVocationCache.primary();
        if (pending != null && synced.filter(pending::equals).isPresent()) {
            pending = null;
        }
        return pending != null ? pending : synced.orElse(null);
    }

    /** Every control on this screen is cut from the same material as the chart behind it. */
    private static ThemedButton chartButton(int x, int y, int w, int h, Component label,
                                            Runnable action) {
        return ThemedButton.skinned(x, y, w, h, label, action,
                McStylePanel.SKIN_STAR_CHART, null, 0,
                SkillTreeChartTextures.CHART_INK, SkillTreeChartTextures.NIGHT_TEXT_DIM);
    }

    /**
     * Re-colours the declared vocation's label in place. Done every frame rather than by rebuilding the
     * widgets on click: the sync answer lands a tick or more after the packet leaves, so a rebuild fired
     * from the press handler still reads the stale cache.
     */
    private void refreshLabels() {
        Identifier active = activeVocation();
        for (int i = 0; i < vocationButtons.size(); i++) {
            VocationDefinition vocation = vocations.get(i);
            boolean current = vocation.id().equals(active);
            vocationButtons.get(i).setMessage(current
                    ? vocation.displayName().copy().withStyle(ChatFormatting.GOLD)
                    : vocation.displayName());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshLabels();
        // No renderBackground() here: the screen framework already ran it for this frame.
        //
        // Same `star_chart` skin as the chart this screen opens from. It was two flat `fill`ed
        // rectangles, which read as a different mod's dialog the moment the screen behind it
        // became night void and brass.
        McStylePanel.drawSkinPanel(graphics,
                McStylePanel.SKIN_STAR_CHART,
                layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH());
        McStylePanel.drawSkinDivider(graphics,
                McStylePanel.SKIN_STAR_CHART,
                layout.panelX() + layout.s(8),
                layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SkillTree.HEADER_HEIGHT),
                layout.panelW() - layout.s(16));
        graphics.drawString(font, title,
                layout.panelX() + layout.s(16), layout.panelY() + layout.s(8),
                WizardsAndBeastsUiTokens.SkillTree.TITLE_COLOR, false);

        if (vocations.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.wizards_and_beasts.vocation.none"),
                    layout.panelX() + layout.s(16), layout.panelY() + layout.s(44),
                    WizardsAndBeastsUiTokens.SkillTree.SUBTEXT_COLOR, false);
        }

        if (focused != null) {
            int textX = layout.panelX() + layout.s(180);
            int textY = layout.panelY() + layout.s(40);
            int wrap = layout.panelW() - layout.s(196);
            graphics.drawString(font, focused.displayName(), textX, textY,
                    WizardsAndBeastsUiTokens.SkillTree.TITLE_COLOR, false);
            textY += layout.s(14);
            for (var line : font.split(focused.pillar(), wrap)) {
                graphics.drawString(font, line, textX, textY,
                        WizardsAndBeastsUiTokens.SkillTree.SUBTEXT_COLOR, false);
                textY += font.lineHeight + 1;
            }
            textY += layout.s(4);
            for (var line : font.split(focused.description(), wrap)) {
                graphics.drawString(font, line, textX, textY,
                        WizardsAndBeastsUiTokens.SkillTree.SUBTEXT_COLOR, false);
                textY += font.lineHeight + 1;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }
}
