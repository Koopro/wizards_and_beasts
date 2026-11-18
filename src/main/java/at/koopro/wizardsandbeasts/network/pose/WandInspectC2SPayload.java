package at.koopro.wizardsandbeasts.network.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.pose.PlayerGesture;
import at.koopro.wizardsandbeasts.pose.PlayerGestures;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Client → server "I am looking at my wand". Carries nothing: whether there is a wand to look at is the server's call.
 *
 * <p>The server plays {@link PlayerGesture#WAND_INSPECT} for everyone watching. Refused silently while the wand is in
 * use (a cast or a Shield Charm is being held) or the hand holds no wand, and rate-limited to one per gesture length
 * so a held key is not a packet stream to every tracker.
 */
@NullMarked
public record WandInspectC2SPayload() implements CustomPacketPayload {

    public static final Type<WandInspectC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wand_inspect_c2s"));

    public static final StreamCodec<ByteBuf, WandInspectC2SPayload> STREAM_CODEC =
            StreamCodec.unit(new WandInspectC2SPayload());

    /** Game time of each player's last accepted inspect. Weak, so a player who leaves takes their entry with them. */
    private static final Map<UUID, Long> LAST = new WeakHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!canInspect(player)) {
                return;
            }
            long now = player.level().getGameTime();
            Long last = LAST.get(player.getUUID());
            if (last != null && now - last < PlayerGesture.WAND_INSPECT.ticks()) {
                return;
            }
            LAST.put(player.getUUID(), now);
            PlayerGestures.play(player, PlayerGesture.WAND_INSPECT);
        });
    }

    /** A wand in the main hand, not being used. Public for the game test. */
    public static boolean canInspect(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof WandItem
                && !player.isUsingItem()
                && !player.isSpectator()
                && !player.isSleeping();
    }
}
