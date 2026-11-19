package at.koopro.wizardsandbeasts.client.equipment.curios;

import at.koopro.wizardsandbeasts.network.equipment.OpenWardrobeC2SPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeRenders;
import at.koopro.wizardsandbeasts.network.equipment.WardrobeRenderPayloads;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.client.ICuriosScreen;

/**
 * The wardrobe's client side of Curios (UI renaissance P6). With {@code CuriosClientRegistration}, the only client
 * code that names Curios types, and API types only.
 *
 * <p>The wardrobe <em>replaces</em> Curios' own screen: when Curios is about to show it (its inventory button or its
 * key), the opening is cancelled and the server is asked for the wardrobe instead, which opens over the same Curios
 * slots. Holding Shift lets Curios' screen through, the way into slot types other mods add that the wardrobe does
 * not lay out. An event, not a mixin: {@link ScreenEvent.Opening} is exactly the hook for swapping a screen.
 */
@NullMarked
public final class WardrobeCuriosClient {

    private WardrobeCuriosClient() {}

    /** Asks the server to flip place {@code place}'s show/hide flag; it syncs every tracking client. */
    public static void toggleRender(int place) {
        ClientPacketDistributor.sendToServer(new WardrobeRenderPayloads.Toggle(place));
    }

    /** A player's show/hide flag changed: set it on this client's copy of their Curios slots. */
    public static void handleRenderSync(WardrobeRenderPayloads.Sync pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            var entity = level == null ? null : level.getEntity(pkt.entityId());
            if (entity != null) {
                WardrobeRenders.apply(entity, pkt.slotId(), pkt.index(), pkt.shown());
            }
        });
    }

    /**
     * What this client believes {@code entity} wears in a Curios slot and whether it is drawn: "item_id render=bool",
     * or "none". Development probes read another player's copy with it (the multiplayer wardrobe check).
     */
    public static String describeSlot(net.minecraft.world.entity.LivingEntity entity, String slotId, int index) {
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventoryOrNull(entity);
        var handler = curios == null ? null : curios.getStacksHandler(slotId).orElse(null);
        if (handler == null || index >= handler.getSlots()) {
            return "none";
        }
        var stack = handler.getStacks().getStackInSlot(index);
        boolean render = index >= handler.getRenders().size() || handler.getRenders().get(index);
        return (stack.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                + " render=" + render;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof ICuriosScreen) || Minecraft.getInstance().hasShiftDown()) {
            return;
        }
        event.setCanceled(true);
        ClientPacketDistributor.sendToServer(OpenWardrobeC2SPayload.INSTANCE);
    }
}
