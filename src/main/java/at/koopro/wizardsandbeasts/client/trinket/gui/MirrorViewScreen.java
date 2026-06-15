package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.network.trinket.MirrorCloseC2SPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The "head in the mirror" — shows the connected player's face (resolved from their skin) and name
 * while a two-way-mirror link is open. Updated live by the server's presence heartbeat.
 */
public class MirrorViewScreen extends Screen {

    @Nullable
    private static MirrorViewScreen active;

    private final UUID otherUuid;
    private final String otherName;
    private float otherYaw;
    private float otherPitch;
    private boolean closingFromServer;

    public MirrorViewScreen(UUID otherUuid, String otherName) {
        super(Component.literal("Two-Way Mirror"));
        this.otherUuid = otherUuid;
        this.otherName = otherName;
    }

    public static void updatePresence(float yaw, float pitch) {
        if (active != null) {
            active.otherYaw = yaw;
            active.otherPitch = pitch;
        }
    }

    public static void closeFromServer(String reason) {
        if (active != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§5" + reason), true);
            }
            active.closingFromServer = true;
            active.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        active = this;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 40, height / 2 + 70, 80, 20).build());
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
        int cy = height / 2;
        int frame = 96;
        // Mirror frame
        graphics.fill(cx - frame / 2 - 4, cy - frame / 2 - 24, cx + frame / 2 + 4, cy + frame / 2 + 8, 0xFF1A1326);
        graphics.renderOutline(cx - frame / 2 - 4, cy - frame / 2 - 24, frame + 8, frame + 32, 0xFF6B4FA0);

        // Face, with a subtle look-driven parallax so the heartbeat reads as "alive".
        int size = frame;
        int parallax = (int) Math.max(-6, Math.min(6, otherYaw / 15f));
        int faceX = cx - size / 2 + parallax;
        int faceY = cy - size / 2 - 6;
        Identifier skin = resolveSkin();
        // Face (u8,v8) + hat overlay (u40,v8), 8x8 region on a 64x64 skin.
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, faceX, faceY, 8f, 8f, size, size, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, faceX, faceY, 40f, 8f, size, size, 8, 8, 64, 64);

        graphics.drawCenteredString(font, "§f" + otherName, cx, cy + size / 2 - 2, 0xFFFFFF);
        graphics.drawCenteredString(font, "§8§oconnected", cx, cy + size / 2 + 10, 0xFFFFFF);

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
