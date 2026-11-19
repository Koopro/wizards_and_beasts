package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.trinket.HowlerItem;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The message a player wrote into a blank Howler.
 *
 * <p>The server seals whichever blank Howler is in the writer's hand; the text is trimmed and capped at
 * {@link HowlerItem#MAX_MESSAGE} there, whatever the client sent.
 */
public record HowlerSealC2SPayload(String text) implements CustomPacketPayload {

    public static final Type<HowlerSealC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "howler_seal"));

    public static final StreamCodec<ByteBuf, HowlerSealC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HowlerSealC2SPayload decode(ByteBuf buf) {
            return new HowlerSealC2SPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, HowlerSealC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.text);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HowlerSealC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || pkt.text == null) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof HowlerItem && !HowlerItem.isSealed(held)) {
                    ItemStack one = held.split(1);
                    if (HowlerItem.seal(one, player.getGameProfile().name(), pkt.text)) {
                        if (held.isEmpty()) {
                            player.setItemInHand(hand, one);
                        } else {
                            at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, one);
                        }
                        player.displayClientMessage(Component.translatable("item.wizards_and_beasts.howler.sealed")
                                .withStyle(ChatFormatting.RED), true);
                    } else {
                        held.grow(1);
                    }
                    return;
                }
            }
        });
    }
}
