package at.koopro.wizardsandbeasts.client.skill.gui;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.skill.state.ClientVocationCache;
import at.koopro.wizardsandbeasts.network.skill.VocationCommitC2SPayload;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * <p>Declaration existed only as {@code /wandb skill vocation set primary <id>}, which is unreachable on a
 * world with cheats off — the whole specialization layer was invisible to a survival player. Deliberately
 * plain: vanilla widgets over the shared panel chrome, no new textures.
 */
public class VocationSelectionScreen extends Screen {

    private final @Nullable Screen parent;
    private final List<VocationDefinition> vocations = new ArrayList<>();
    private @Nullable VocationDefinition focused;
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

        layout = GuiScaleHelper.Layout.panel(width, height,
                WizardsAndBeastsUiTokens.SkillTree.PANEL_WIDTH, WizardsAndBeastsUiTokens.SkillTree.PANEL_HEIGHT);

        Optional<Identifier> declared = ClientVocationCache.primary();
        int buttonW = layout.s(150);
        int buttonH = layout.s(20);
        int x = layout.panelX() + layout.s(16);
        int y = layout.panelY() + layout.s(40);

        for (VocationDefinition vocation : vocations) {
            boolean current = declared.filter(id -> id.equals(vocation.id())).isPresent();
            Component label = current
                    ? vocation.displayName().copy().withStyle(ChatFormatting.GOLD)
                    : vocation.displayName();
            Button button = Button.builder(label, b -> declare(vocation))
                    .bounds(x, y, buttonW, buttonH)
                    .build();
            addRenderableWidget(button);
            y += buttonH + layout.s(4);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(layout.panelX() + layout.panelW() - layout.s(16) - layout.s(80),
                        layout.panelY() + layout.panelH() - layout.s(28), layout.s(80), buttonH)
                .build());

        focused = declared.map(VocationRegistry::get).orElse(vocations.isEmpty() ? null : vocations.get(0));
    }

    private void declare(VocationDefinition vocation) {
        ClientPacketDistributor.sendToServer(new VocationCommitC2SPayload(vocation.id().toString()));
        focused = vocation;
        // The server answers with a fresh VocationDataSyncS2CPayload; re-init picks up the gold highlight.
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        graphics.fill(layout.panelX(), layout.panelY(),
                layout.panelX() + layout.panelW(), layout.panelY() + layout.panelH(),
                WizardsAndBeastsUiTokens.SkillTree.PANEL_FILL);
        graphics.fill(layout.panelX(), layout.panelY(),
                layout.panelX() + layout.panelW(),
                layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SkillTree.HEADER_HEIGHT),
                WizardsAndBeastsUiTokens.SkillTree.HEADER_FILL);
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
