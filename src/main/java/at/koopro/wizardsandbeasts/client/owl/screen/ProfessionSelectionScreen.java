package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.network.owl.ChooseProfessionPacket;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public class ProfessionSelectionScreen extends Screen {

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 220;
    private static final int ROW_HEIGHT = 14;

    private final Map<OWLSubject, OWLGrade> grades;
    private int scrollOffset = 0;

    private float guiScale = 1.0f;
    private int originX;
    private int originY;
    private int designLeft;
    private int designTop;

    public ProfessionSelectionScreen(@NonNull Map<OWLSubject, OWLGrade> grades) {
        super(Component.translatable("owls.screen.profession_title"));
        this.grades = grades;
    }

    /** Map an unscaled design-space x (relative to current width/height) to screen space. */
    private int sx(int designX) {
        return originX + Math.round((designX - designLeft) * guiScale);
    }

    private int sy(int designY) {
        return originY + Math.round((designY - designTop) * guiScale);
    }

    private int sw(int size) {
        return Math.max(1, Math.round(size * guiScale));
    }

    @Override
    protected void init() {
        super.init();
        guiScale = GuiScaleHelper.computeScale(BG_WIDTH, BG_HEIGHT, width, height, GuiScaleHelper.DEFAULT_MARGIN);
        designLeft = width / 2 - BG_WIDTH / 2;
        designTop = height / 2 - BG_HEIGHT / 2;
        originX = GuiScaleHelper.clampedLeft(Math.round(BG_WIDTH * guiScale), width, GuiScaleHelper.DEFAULT_MARGIN);
        originY = GuiScaleHelper.clampedTop(Math.round(BG_HEIGHT * guiScale), height, GuiScaleHelper.DEFAULT_MARGIN);
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int cx = width / 2;
        int startY = height / 2 - BG_HEIGHT / 2 + 28;

        Profession[] profs = Profession.values();
        for (int i = 0; i < profs.length; i++) {
            Profession prof = profs[i];
            int rowY = startY + i * (ROW_HEIGHT + 2) - scrollOffset;
            if (rowY < startY || rowY > height / 2 + BG_HEIGHT / 2 - 24) continue;

            boolean eligible = isClientEligible(prof);
            if (eligible) {
                Profession finalProf = prof;
                addRenderableWidget(Button.builder(
                        Component.translatable("owls.button.choose"),
                        btn -> confirmChoose(finalProf))
                        .pos(sx(cx + BG_WIDTH / 2 - 50), sy(rowY))
                        .size(sw(44), sw(12))
                        .build());
            }
        }

        addRenderableWidget(Button.builder(
                Component.literal("▲"),
                btn -> { scrollOffset = Math.max(0, scrollOffset - ROW_HEIGHT * 2); rebuildButtons(); })
                .pos(sx(width / 2 + BG_WIDTH / 2 - 14), sy(height / 2 - BG_HEIGHT / 2 + 8))
                .size(sw(12), sw(12))
                .build());
        addRenderableWidget(Button.builder(
                Component.literal("▼"),
                btn -> { scrollOffset += ROW_HEIGHT * 2; rebuildButtons(); })
                .pos(sx(width / 2 + BG_WIDTH / 2 - 14), sy(height / 2 + BG_HEIGHT / 2 - 22))
                .size(sw(12), sw(12))
                .build());
    }

    private boolean isClientEligible(Profession prof) {
        // Quick client-side eligibility check based on cached grades.
        // Server re-validates before committing.
        return switch (prof) {
            case QUIDDITCH_PLAYER, JOURNALIST -> true;
            default -> {
                Map<OWLSubject, OWLGrade> g = grades;
                // Delegate to shared logic — pass null for player (heritage check skipped client-side)
                // We approximate by always treating CURSE_BREAKER without goblin bonus here.
                yield approximateEligible(prof, g);
            }
        };
    }

    private boolean approximateEligible(Profession prof, Map<OWLSubject, OWLGrade> g) {
        return switch (prof) {
            case AUROR -> meets(g, OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E)
                    && meets(g, OWLSubject.POTIONS, OWLGrade.E)
                    && meets(g, OWLSubject.TRANSFIGURATION, OWLGrade.E)
                    && meets(g, OWLSubject.CHARMS, OWLGrade.E)
                    && meets(g, OWLSubject.HERBOLOGY, OWLGrade.E);
            case HEALER -> meets(g, OWLSubject.TRANSFIGURATION, OWLGrade.E)
                    && meets(g, OWLSubject.POTIONS, OWLGrade.E)
                    && meets(g, OWLSubject.HERBOLOGY, OWLGrade.E)
                    && meets(g, OWLSubject.CHARMS, OWLGrade.E)
                    && meets(g, OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E);
            case WANDMAKER -> meets(g, OWLSubject.ANCIENT_RUNES, OWLGrade.A)
                    && meets(g, OWLSubject.CHARMS, OWLGrade.A);
            case MAGIZOOLOGIST -> meets(g, OWLSubject.CARE_OF_MAGICAL_CREATURES, OWLGrade.A)
                    && meets(g, OWLSubject.CHARMS, OWLGrade.A);
            case CURSE_BREAKER -> meets(g, OWLSubject.ARITHMANCY, OWLGrade.E)
                    && meets(g, OWLSubject.ANCIENT_RUNES, OWLGrade.E);
            case POTIONEER -> meets(g, OWLSubject.POTIONS, OWLGrade.O)
                    && meets(g, OWLSubject.HERBOLOGY, OWLGrade.E);
            case HIT_WIZARD -> meets(g, OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.O)
                    && meets(g, OWLSubject.TRANSFIGURATION, OWLGrade.E)
                    && meets(g, OWLSubject.CHARMS, OWLGrade.E);
            case OBLIVIATOR -> meets(g, OWLSubject.CHARMS, OWLGrade.O)
                    && meets(g, OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E);
            case UNSPEAKABLE -> meets(g, OWLSubject.ARITHMANCY, OWLGrade.O)
                    && meets(g, OWLSubject.ANCIENT_RUNES, OWLGrade.O)
                    && meets(g, OWLSubject.TRANSFIGURATION, OWLGrade.O);
            case PROFESSOR -> g.values().stream().anyMatch(grade -> grade == OWLGrade.O);
            case ARITHMANCER -> meets(g, OWLSubject.ARITHMANCY, OWLGrade.O)
                    && meets(g, OWLSubject.ANCIENT_RUNES, OWLGrade.E);
            case HERBOLOGIST -> meets(g, OWLSubject.HERBOLOGY, OWLGrade.O)
                    && meets(g, OWLSubject.POTIONS, OWLGrade.E);
            default -> false;
        };
    }

    private static boolean meets(Map<OWLSubject, OWLGrade> grades, OWLSubject subject, OWLGrade min) {
        return grades.getOrDefault(subject, OWLGrade.T).value >= min.value;
    }

    private void confirmChoose(Profession profession) {
        assert minecraft != null;
        minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        ClientPacketDistributor.sendToServer(new ChooseProfessionPacket(profession));
                        minecraft.setScreen(null);
                    } else {
                        minecraft.setScreen(ProfessionSelectionScreen.this);
                    }
                },
                Component.translatable("owls.confirm.profession_title"),
                Component.translatable("owls.confirm.profession_body",
                        Component.translatable(profession.translationKey()))
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX - designLeft * guiScale, originY - designTop * guiScale);
        pose.scale(guiScale, guiScale);

        graphics.fill(cx - BG_WIDTH / 2, cy - BG_HEIGHT / 2, cx + BG_WIDTH / 2, cy + BG_HEIGHT / 2, 0xEEF5E6C8);
        graphics.drawCenteredString(font,
                Component.translatable("owls.screen.profession_title").withColor(0x5C3317),
                cx, cy - BG_HEIGHT / 2 + 6, 0x5C3317);

        int startY = cy - BG_HEIGHT / 2 + 28;
        for (Profession prof : Profession.values()) {
            int rowY = startY + prof.ordinal() * (ROW_HEIGHT + 2) - scrollOffset;
            if (rowY < startY || rowY > cy + BG_HEIGHT / 2 - 24) continue;

            boolean eligible = isClientEligible(prof);
            int nameColor = eligible ? 0xCC8800 : 0x888888;
            graphics.drawString(font, Component.translatable(prof.translationKey()), cx - BG_WIDTH / 2 + 10, rowY, nameColor, false);
        }

        pose.popMatrix();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
