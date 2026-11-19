package at.koopro.wizardsandbeasts.network.equipment;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeMenu;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeRenders;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * The wardrobe eye's two packets. The client asks to toggle a <em>place</em> (an index into the wardrobe layout),
 * never a slot of its choosing on an entity of its choosing: the server flips the sender's own flag, only while the
 * sender has the wardrobe open, and tells everyone who can see them.
 */
@NullMarked
public final class WardrobeRenderPayloads {

    private WardrobeRenderPayloads() {}

    public record Toggle(int place) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wardrobe_toggle_render"));
        public static final StreamCodec<ByteBuf, Toggle> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Toggle decode(ByteBuf buf) {
                return new Toggle(buf.readInt());
            }

            @Override
            public void encode(ByteBuf buf, Toggle pkt) {
                buf.writeInt(pkt.place);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Sync(int entityId, String slotId, int index, boolean shown) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wardrobe_render_sync"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Sync decode(ByteBuf buf) {
                return new Sync(buf.readInt(), PacketCodecUtils.readString(buf), buf.readInt(), buf.readBoolean());
            }

            @Override
            public void encode(ByteBuf buf, Sync pkt) {
                buf.writeInt(pkt.entityId);
                PacketCodecUtils.writeString(buf, pkt.slotId);
                buf.writeInt(pkt.index);
                buf.writeBoolean(pkt.shown);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Toggle.TYPE, Toggle.STREAM_CODEC, (pkt, ctx) -> ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof WardrobeMenu)) {
                return;
            }
            WardrobeRenders.Change change = WardrobeRenders.toggle(player, pkt.place());
            if (change != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new Sync(change.entityId(), change.slotId(), change.index(), change.shown()));
            }
        }));
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC,
                at.koopro.wizardsandbeasts.client.equipment.curios.WardrobeCuriosClient::handleRenderSync);
    }
}
