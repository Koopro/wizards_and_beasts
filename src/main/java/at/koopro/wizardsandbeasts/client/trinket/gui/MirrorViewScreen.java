package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.network.trinket.MirrorCloseC2SPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * The "head in the mirror" — shows the connected player's face (resolved from their skin) and name
 * while a two-way-mirror link is open. Updated live by the server's presence heartbeat.
 *
 * <p>It <em>is</em> the mirror: dark silvered glass with the other wizard's face in it, in the engraved
 * pewter frame the call screen ({@link MirrorCallScreen}) wears, the name on a pewter plaque beneath
 * it like the plate under a portrait, pewter buttons ({@link MirrorChrome}).
 */
public class MirrorViewScreen extends Screen {

    /** The face, in pixels: one skin texel is twelve. */
    private static final int FACE = 96;
    /** Glass either side of the face; the look parallax is clamped to it, so the face stays in. */
    private static final int GLASS_MARGIN = 6;
    private static final int GLASS = FACE + 2 * GLASS_MARGIN;
    /** The pewter frame, up to where the glass begins. */
    private static final int FRAME_W = MirrorChrome.FRAME;
    private static final int PLATE_PAD_X = WizardsMetrics.SPACE_L;
    private static final int PLATE_PAD_Y = WizardsMetrics.SPACE_M;
    private static final int PLATE_H = PLATE_PAD_Y + WizardsMetrics.LINE_BODY + 9 + PLATE_PAD_Y;
    private static final int BUTTON_W = 80;
    private static final int BUTTON_H = 20;
    private static final int GAP = 6;
    /** Glass, frame, plate and button, stacked; the whole is centred on the screen. */
    private static final int STACK_H = GLASS + 2 * FRAME_W + GAP + PLATE_H + GAP + BUTTON_H;

    @Nullable
    private static MirrorViewScreen active;

    private final UUID otherUuid;
    private final String otherName;
    private float otherYaw;
    private boolean closingFromServer;

    public MirrorViewScreen(UUID otherUuid, String otherName) {
        super(Component.translatable(MirrorCallScreen.KEY + "title"));
        this.otherUuid = otherUuid;
        this.otherName = otherName;
    }

    public static void updatePresence(float yaw, float pitch) {
        if (active != null) {
            active.otherYaw = yaw;
        }
    }

    public static void closeFromServer(String reason) {
        if (active != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // The reason is the server's own sentence; its purple comes from the style, not a section code.
                mc.player.displayClientMessage(Component.literal(reason).withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            active.closingFromServer = true;
            active.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        active = this;
        addRenderableWidget(ThemedButton.skinned(width / 2 - BUTTON_W / 2,
                stackTop() + STACK_H - BUTTON_H, BUTTON_W, BUTTON_H,
                Component.translatable(MirrorCallScreen.KEY + "close"), this::onClose, MirrorChrome.BUTTONS, null, 0,
                MirrorChrome.INK, MirrorChrome.INK_OFF));
    }

    private int stackTop() {
        return (height - STACK_H) / 2;
    }

    @Override
    public void onClose() {
        if (active == this) {
            active = null;
        }
        if (!closingFromServer) {
            ClientPacketDistributor.sendToServer(new MirrorCloseC2SPayload());
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);

        int cx = width / 2;
        int top = stackTop();
        int glassX = cx - GLASS / 2;
        int glassY = top + FRAME_W;
        // Dark silvered glass in the engraved pewter frame the call screen wears; the face is drawn into
        // the glass, and the frame goes over it last so the face never crosses the metal.
        MirrorChrome.mirror(graphics, glassX - FRAME_W, glassY - FRAME_W, GLASS + 2 * FRAME_W, GLASS + 2 * FRAME_W, true);

        // Face, with a subtle look-driven parallax so the heartbeat reads as "alive".
        int parallax = (int) Math.max(-GLASS_MARGIN, Math.min(GLASS_MARGIN, otherYaw / 15f));
        int faceX = cx - FACE / 2 + parallax;
        int faceY = glassY + GLASS_MARGIN;
        Identifier skin = resolveSkin();
        // Face (u8,v8) + hat overlay (u40,v8), 8x8 region on a 64x64 skin.
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, faceX, faceY, 8f, 8f, FACE, FACE, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, faceX, faceY, 40f, 8f, FACE, FACE, 8, 8, 64, 64);
        MirrorChrome.frame(graphics, glassX - FRAME_W, glassY - FRAME_W, GLASS + 2 * FRAME_W, GLASS + 2 * FRAME_W);

        // The name on an engraved pewter plaque under the glass, like the plate under a portrait.
        Component name = Component.literal(otherName);
        Component status = Component.translatable(MirrorCallScreen.KEY + "connected").withStyle(ChatFormatting.ITALIC);
        int plateW = Math.max(GLASS + 2 * FRAME_W,
                Math.max(font.width(name), font.width(status)) + 2 * PLATE_PAD_X);
        int plateY = glassY + GLASS + FRAME_W + GAP;
        MirrorChrome.plaque(graphics, cx - plateW / 2, plateY, plateW, PLATE_H);
        int textY = plateY + PLATE_PAD_Y;
        graphics.drawString(font, name, cx - font.width(name) / 2, textY, MirrorChrome.INK, false);
        graphics.drawString(font, status, cx - font.width(status) / 2,
                textY + WizardsMetrics.LINE_BODY, MirrorChrome.INK_2, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Identifier resolveSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(otherUuid);
            if (p instanceof AbstractClientPlayer acp) {
                return acp.getSkin().body().texturePath();
            }
        }
        return DefaultPlayerSkin.get(new GameProfile(otherUuid, otherName)).body().texturePath();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
