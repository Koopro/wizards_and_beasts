package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.equipment.curios.WardrobeCuriosClient;
import at.koopro.wizardsandbeasts.equipment.WardrobeLayout;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeMenu;
import at.koopro.wizardsandbeasts.network.equipment.OpenWardrobeC2SPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Two real clients on one dedicated server, checking the wardrobe in multiplayer (UI renaissance P7). Development
 * only; inert unless {@code WB_WARDROBE_MP} is {@code wearer} or {@code watcher}, with {@code WB_WARDROBE_MP_ADDR}
 * the server and {@code WB_WARDROBE_MP_PEER} the other client's name.
 *
 * <p>The wearer (opped) gives itself a hat, opens the wardrobe with the real packet, shift-clicks the hat in (the
 * menu's server-side quick move), then clicks the head slot's eye (the real toggle packet). The watcher, spawned at
 * the same point, logs once a second what <em>its</em> copy of the wearer's Curios head slot holds and whether it is
 * drawn, and screenshots the wearer at the end. Every line starts {@code WardrobeMp:} for the run script to grep.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class WardrobeMpProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final @Nullable String ROLE = FMLEnvironment.isProduction() ? null : System.getenv("WB_WARDROBE_MP");
    private static final String ADDR = System.getenv().getOrDefault("WB_WARDROBE_MP_ADDR", "127.0.0.1:25601");
    private static final String PEER = System.getenv().getOrDefault("WB_WARDROBE_MP_PEER", "");

    private static boolean connecting;
    private static long joinedAt = -1L;
    private static int stage;
    private static long lastLog;

    private WardrobeMpProbe() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ROLE == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!connecting && mc.screen instanceof TitleScreen) {
            connecting = true;
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(ADDR),
                    new ServerData("wardrobe-mp", ADDR, ServerData.Type.OTHER), false, null);
            LOGGER.info("WardrobeMp: {} connecting to {}", ROLE, ADDR);
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (joinedAt < 0) {
            joinedAt = now;
            LOGGER.info("WardrobeMp: {} joined as {}", ROLE, mc.player.getName().getString());
        }
        long t = now - joinedAt;
        if ("wearer".equals(ROLE)) {
            wearer(mc, t);
        } else if ("watcher".equals(ROLE)) {
            watcher(mc, t, now);
        }
    }

    private static void wearer(Minecraft mc, long t) {
        if (stage == 0 && t > 15_000) {
            mc.player.connection.sendCommand("give @s wizards_and_beasts:wizard_hat");
            LOGGER.info("WardrobeMp: wearer gave itself a hat");
            stage = 1;
        } else if (stage == 1 && t > 17_000) {
            ClientPacketDistributor.sendToServer(OpenWardrobeC2SPayload.INSTANCE);
            stage = 2;
        } else if (stage == 2 && t > 19_000) {
            if (!(mc.player.containerMenu instanceof WardrobeMenu menu)) {
                LOGGER.info("WardrobeMp: FAIL wearer has no wardrobe open ({})", mc.player.containerMenu);
                stage = 9;
                return;
            }
            int hatSlot = -1;
            for (int i = WardrobeLayout.WARDROBE_SLOTS; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot.getItem().getItem())
                        .getPath().equals("wizard_hat")) {
                    hatSlot = i;
                    break;
                }
            }
            if (hatSlot < 0) {
                LOGGER.info("WardrobeMp: FAIL wearer found no hat in its inventory");
                stage = 9;
                return;
            }
            mc.gameMode.handleInventoryMouseClick(menu.containerId, hatSlot, 0, ClickType.QUICK_MOVE, mc.player);
            LOGGER.info("WardrobeMp: wearer shift-clicked menu slot {}", hatSlot);
            stage = 3;
        } else if (stage == 3 && t > 23_000) {
            LOGGER.info("WardrobeMp: wearer own head {}",
                    WardrobeCuriosClient.describeSlot(mc.player, "head", 0));
            // the head slot's eye: place 0
            WardrobeCuriosClient.toggleRender(0);
            LOGGER.info("WardrobeMp: wearer clicked the head eye");
            stage = 4;
        } else if (stage == 4 && t > 27_000) {
            LOGGER.info("WardrobeMp: wearer own head after toggle {}",
                    WardrobeCuriosClient.describeSlot(mc.player, "head", 0));
            mc.player.closeContainer();
            stage = 5;
        } else if (stage == 5 && t > 60_000) {
            LOGGER.info("WardrobeMp: wearer done");
            mc.stop();
        }
    }

    private static void watcher(Minecraft mc, long t, long now) {
        AbstractClientPlayer peer = null;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p != mc.player && (PEER.isEmpty() || p.getName().getString().equals(PEER))) {
                peer = p;
            }
        }
        if (now - lastLog >= 1000) {
            lastLog = now;
            LOGGER.info("WardrobeMp: watcher t={}s sees {} head {}", t / 1000,
                    peer == null ? "nobody" : peer.getName().getString(),
                    peer == null ? "-" : WardrobeCuriosClient.describeSlot(peer, "head", 0));
        }
        if (peer != null && stage == 0) {
            // stand back and face the wearer, so the screenshot shows them
            mc.player.connection.sendCommand("tp @s ~ ~ ~3");
            stage = 1;
        }
        if (peer != null && stage == 1 && t > 26_000) {
            mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, peer.getEyePosition());
            stage = 2;
        }
        if (stage == 2 && t > 28_000) {
            Screenshot.grab(mc.gameDirectory, "wardrobe_mp_watcher.png", mc.getMainRenderTarget(), 1, c -> { });
            stage = 3;
        }
        if (t > 62_000) {
            LOGGER.info("WardrobeMp: watcher done");
            mc.stop();
        }
    }
}
