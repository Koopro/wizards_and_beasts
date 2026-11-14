package at.koopro.wizardsandbeasts.client.diagon;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.diagon.DiagonAlley;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Stepping through the Leaky Cauldron's back door, in either direction: the dim of the passage and a drift of brick dust
 * for the moment the other side takes to arrive, then the world fades back in. Never the "Loading terrain" screen.
 *
 * <p>Registered through NeoForge's dimension transition hook for travel into and out of Diagon Alley's pocket. The
 * screen still closes the way the vanilla one does, when the client's level is ready; the pocket's chunks are kept
 * loaded on the server, so that is a moment, not a wait.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class DiagonPassageScreen extends LevelLoadingScreen {

    /** The passage's colour: the dark of an old pub's back corridor. */
    private static final int DARK = 0xFF0E0B09;
    private static final int DUST = 0x30C9A27E;
    /** How long the world takes to fade back in once the screen closes. */
    private static final long FADE_IN_MS = 450L;
    private static long closedAt = -1L;

    private final long openedAt = Util.getMillis();

    public DiagonPassageScreen(LevelLoadTracker tracker, Reason reason) {
        super(tracker, reason);
    }

    @SubscribeEvent
    static void onRegisterTransitions(RegisterDimensionTransitionScreenEvent event) {
        event.registerIncomingEffect(DiagonAlley.LEVEL, DiagonPassageScreen::new);
        event.registerOutgoingEffect(DiagonAlley.LEVEL, DiagonPassageScreen::new);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DARK);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // A slow drift of dust motes across the dark, seeded so they do not jump frame to frame.
        float t = (Util.getMillis() - openedAt) / 1000f;
        for (int i = 0; i < 40; i++) {
            float seedX = Mth.frac(Mth.sin(i * 12.9898f) * 43758.547f);
            float seedY = Mth.frac(Mth.sin(i * 78.233f) * 12345.679f);
            int x = (int) (Mth.frac(seedX + t * 0.03f * (0.5f + seedY)) * width);
            int y = (int) (Mth.frac(seedY - t * 0.05f) * height);
            int s = 1 + (i % 3);
            graphics.fill(x, y, x + s, y + s, DUST);
        }
    }

    @Override
    public void onClose() {
        closedAt = Util.getMillis();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The fade back into the world, drawn over everything for a moment after the passage closes. */
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        if (closedAt < 0) {
            return;
        }
        long since = Util.getMillis() - closedAt;
        if (since >= FADE_IN_MS) {
            closedAt = -1L;
            return;
        }
        int alpha = (int) (255 * (1f - since / (float) FADE_IN_MS));
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | (DARK & 0xFFFFFF));
    }
}
