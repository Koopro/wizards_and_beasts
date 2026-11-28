package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * "Study this chapter": the reader picked a spell on the book screen. Names the hand and the spell only; the
 * server checks the book is still in that hand, still teaches the spell, and was read just now
 * ({@link SpellBookService#studyChapter}), then opens the trace.
 */
@NullMarked
public record SpellBookChapterC2SPayload(int hand, String spellId) implements CustomPacketPayload {

    public static final Type<SpellBookChapterC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_book_chapter"));

    public static final StreamCodec<ByteBuf, SpellBookChapterC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellBookChapterC2SPayload decode(ByteBuf buf) {
            return new SpellBookChapterC2SPayload(buf.readByte(), PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SpellBookChapterC2SPayload pkt) {
            buf.writeByte(pkt.hand);
            PacketCodecUtils.writeString(buf, pkt.spellId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellBookChapterC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && pkt.hand >= 0
                    && pkt.hand < InteractionHand.values().length) {
                SpellBookService.studyChapter(player, InteractionHand.values()[pkt.hand], pkt.spellId);
            }
        });
    }
}
