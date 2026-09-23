package at.koopro.wizardsandbeasts.client.ministry.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.ministry.licence.LicenceUpgrade;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseData;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The licence, as the holder reads it: a parchment card with the Ministry's seal turning above it.
 *
 * <p>Every value on the page comes off the stack's own {@link LicenseData} component, re-read each
 * frame. Nothing was sent to open this screen but a hand — so the card cannot drift out of step with
 * the tooltip, and a licence revoked while the screen is open turns black under the player's hands
 * rather than lying to them until they close it.
 *
 * <p>The seal is drawn, not textured: two counter-rotating rings of ticks around a sunburst, in
 * Ministry violet. That is what makes the revocation read instantly — a seal whose <em>colour</em>
 * carries the meaning can go black without needing a second piece of art, and a black seal that is
 * still turning is a more unpleasant image than a stamp that simply changed.
 */
public class MinistryLicenceScreen extends Screen {

    private static final int PANEL_W = 248;
    private static final int PANEL_H = 168;

    /** Seal centre, in design space, relative to the panel corner. */
    private static final int SEAL_CX = PANEL_W - 52;
    private static final int SEAL_CY = 96;
    private static final int SEAL_OUTER = 30;
    private static final int SEAL_INNER = 17;
    /** Ticks around each ring. Twelve reads as a clock face without turning into a solid disc. */
    private static final int SEAL_TICKS = 12;
    /** Seconds per revolution of the outer ring. The inner one runs the other way, slightly faster. */
    private static final float SEAL_PERIOD_SECONDS = 8.0f;

    private static final int REVOKED_SEAL = 0xFF12090F;
    private static final int REVOKED_SEAL_DIM = 0xFF2A1620;

    /** Cream memo stock and the Ministry's purple-black ink: the licence is a Ministry document. */
    private static final GuiSkin SKIN = GuiSkin.MINISTRY_MEMO;
    /** Content inset from the card's edge: clear of its double ink rule, 4 and 6px in. */
    private static final int PAD = 12;

    private final boolean offHand;
    private GuiScaleHelper.Layout layout;
    private int panelX;
    private int panelY;

    public MinistryLicenceScreen(boolean offHand) {
        super(Component.translatable("gui.wizards_and_beasts.licence.title"));
        this.offHand = offHand;
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.fit(width, height, PANEL_W, PANEL_H);
        panelX = layout.panelX();
        panelY = layout.panelY();
    }

    /** The scroll this screen was opened on, re-read every frame rather than captured. */
    private @Nullable LicenseData document() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        ItemStack held = offHand ? player.getOffhandItem() : player.getMainHandItem();
        return MinistryLicences.read(held);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        layout.applyScale(graphics);

        LicenseData data = document();
        boolean revoked = data != null && data.revoked();

        drawCard(graphics, revoked);
        drawSeal(graphics, revoked);

        if (data == null) {
            String blank = Component.translatable("gui.wizards_and_beasts.licence.blank").getString();
            graphics.drawString(font, blank, panelX + PANEL_W / 2 - font.width(blank) / 2,
                    panelY + PANEL_H / 2 - 4, SKIN.ink(), false);
            graphics.pose().popMatrix();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        drawFields(graphics, data, revoked);

        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The card: the Ministry's memo sheet, its heading written on it in the Ministry's purple.
     *
     * <p>It used to be a flat fill with a purple band across the top and the heading reversed out
     * of it in cream. A band painted over the sheet would cover the frame's own rules, so the
     * purple moved from the ground to the lettering; a revoked licence's heading goes black with
     * its seal.
     */
    private void drawCard(GuiGraphics graphics, boolean revoked) {
        McStylePanel.drawSkinPanel(graphics, SKIN, panelX, panelY, PANEL_W, PANEL_H);
        graphics.drawString(font,
                Component.translatable("gui.wizards_and_beasts.licence.header").getString(),
                panelX + PAD, panelY + 10, revoked ? REVOKED_SEAL : WizardsPalette.MINISTRY, false);
        McStylePanel.drawSkinDivider(graphics, SKIN, panelX + PAD, panelY + 18, PANEL_W - 2 * PAD);
    }

    /**
     * Two counter-rotating rings of ticks around a sunburst.
     *
     * <p>Wall clock rather than game time: this is a render-rate animation and quantising it to 20 Hz
     * would make a slow turn visibly step. It also keeps turning while the game is paused, which is
     * the right behaviour for a hologram and the wrong one for anything that models the world.
     */
    private void drawSeal(GuiGraphics graphics, boolean revoked) {
        int cx = panelX + SEAL_CX;
        int cy = panelY + SEAL_CY;
        float seconds = (System.currentTimeMillis() % 600_000L) / 1000.0f;
        float phase = (seconds / SEAL_PERIOD_SECONDS) * Mth.TWO_PI;

        int bright = revoked ? REVOKED_SEAL : WizardsPalette.MINISTRY_LIGHT;
        int dim = revoked ? REVOKED_SEAL_DIM : WizardsPalette.MINISTRY;

        // A hologram is light, not paint: the plate under it is a recessed well of the sheet, so the
        // ticks read as hovering rather than as a printed stamp. 64 square, the well's native size.
        McStylePanel.drawSkinInset(graphics, SKIN, cx - SEAL_OUTER - 2, cy - SEAL_OUTER - 2,
                2 * SEAL_OUTER + 4, 2 * SEAL_OUTER + 4);

        drawRing(graphics, cx, cy, SEAL_OUTER, phase, bright);
        drawRing(graphics, cx, cy, SEAL_INNER, -phase * 1.4f, dim);

        // Sunburst: four spokes on the diagonal, so the two rings never line up with it at once.
        for (int spoke = 0; spoke < 4; spoke++) {
            double angle = phase * 0.5 + spoke * (Math.PI / 2.0) + Math.PI / 4.0;
            for (int step = 3; step <= 9; step++) {
                int px = cx + (int) Math.round(Math.cos(angle) * step);
                int py = cy + (int) Math.round(Math.sin(angle) * step);
                graphics.fill(px, py, px + 1, py + 1, bright);
            }
        }
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, bright);
    }

    private static void drawRing(GuiGraphics graphics, int cx, int cy, int radius, float phase, int colour) {
        for (int i = 0; i < SEAL_TICKS; i++) {
            double angle = phase + (Mth.TWO_PI * i) / SEAL_TICKS;
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            // Every third tick is a longer mark, which is what stops a ring of identical dots from
            // looking motionless when it is turning slowly.
            int size = (i % 3 == 0) ? 3 : 2;
            graphics.fill(px - size / 2, py - size / 2, px - size / 2 + size, py - size / 2 + size, colour);
        }
    }

    private void drawFields(GuiGraphics graphics, LicenseData data, boolean revoked) {
        var player = Minecraft.getInstance().player;
        int x = panelX + PAD;
        int y = panelY + 32;
        int line = 13;

        line(graphics, x, y, "gui.wizards_and_beasts.licence.field.type", data.type().displayName());
        y += line;
        line(graphics, x, y, "gui.wizards_and_beasts.licence.field.rank",
                Component.literal(data.rank() + " / " + LicenseType.MAX_RANK));
        y += line;
        line(graphics, x, y, "gui.wizards_and_beasts.licence.field.holder",
                Component.literal(player == null ? "—" : player.getGameProfile().name()));
        y += line;
        line(graphics, x, y, "gui.wizards_and_beasts.licence.field.expiry",
                data.isPermanent()
                        ? Component.translatable("gui.wizards_and_beasts.licence.permanent")
                        : Component.literal(String.valueOf(data.expiryTick())));
        y += line;
        line(graphics, x, y, "gui.wizards_and_beasts.licence.field.examined",
                Component.translatable(data.type().examinedSubject().translationKey()));
        y += line + 4;

        if (revoked) {
            // Stamped on the card in red ink, boxed to the words: a black bar with red lettering
            // reversed out of it was a strip of screen chrome laid over the paper. Sized to the text
            // rather than the card, so the stamp stops short of the seal's well.
            Component banner = Component.translatable("ministry.wizards_and_beasts.licence.revoked_banner")
                    .withStyle(ChatFormatting.BOLD);
            graphics.renderOutline(panelX + PAD - 3, y - 3, font.width(banner) + 6, 15, WizardsPalette.PAGE_BAD);
            graphics.drawString(font, banner, panelX + PAD, y + 1, WizardsPalette.PAGE_BAD, false);
            return;
        }

        OWLGrade required = LicenceUpgrade.requirementFor(data.rank() + 1);
        String next = required == null
                ? Component.translatable("gui.wizards_and_beasts.licence.fully_endorsed").getString()
                : Component.translatable("gui.wizards_and_beasts.licence.next_rank",
                        Component.translatable(required.translationKey()),
                        Component.translatable(data.type().examinedSubject().translationKey())).getString();
        graphics.drawString(font, next, x, y, SKIN.ink(), false);
    }

    /** A field: its label in the sheet's thin ink, its value in the Ministry's purple. */
    private void line(GuiGraphics graphics, int x, int y, String labelKey, Component value) {
        String label = Component.translatable(labelKey).getString();
        graphics.drawString(font, label, x, y, SKIN.muted(), false);
        graphics.drawString(font, value.getString(), x + 74, y, WizardsPalette.MINISTRY, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Opened from the payload handler; the hand is all the server sent. */
    public static MinistryLicenceScreen forHand(InteractionHand hand) {
        return new MinistryLicenceScreen(hand == InteractionHand.OFF_HAND);
    }
}
