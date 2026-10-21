package at.koopro.wizardsandbeasts.client.hud;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.education.ClientEducationState;
import at.koopro.wizardsandbeasts.client.gui.character.tab.EducationTab;
import at.koopro.wizardsandbeasts.education.EducationView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The examination clock: while a sitting is open, one line at the top of the screen — the level, the time left and
 * the papers done — so a candidate out doing practicals knows when to be back at the desk. Information, not an
 * effect, so it ignores {@code reduceScreenEffects}; it hides with the rest of the HUD (F1).
 */
public final class ExamSittingOverlay {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "exam_sitting");

    private static final int TEXT = 0xFFF2E6C8;
    private static final int URGENT = 0xFFFF7A5C;
    /** The last minute is drawn in red. */
    private static final long URGENT_TICKS = 1200L;

    private ExamSittingOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        EducationView.SittingView sitting = ClientEducationState.get().sitting();
        if (sitting == null) {
            return;
        }
        long left = sitting.deadline() - mc.level.getGameTime();
        long done = sitting.papers().stream().filter(EducationView.PaperLine::done).count();
        Component line = Component.translatable("education.hud.sitting",
                Component.translatable(sitting.level().translationKey()), EducationTab.clock(left), done,
                sitting.papers().size());
        int w = mc.font.width(line);
        int x = graphics.guiWidth() / 2 - w / 2;
        int y = 4;
        graphics.fill(x - 4, y - 2, x + w + 4, y + mc.font.lineHeight + 1, 0x88000000);
        graphics.drawString(mc.font, line, x, y, left <= URGENT_TICKS ? URGENT : TEXT, true);
    }
}
