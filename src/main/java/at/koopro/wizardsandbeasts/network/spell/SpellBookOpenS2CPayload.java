package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A textbook read through: its chapters and where the reader stands with each, judged on the server
 * ({@code SpellBookService}). Opens the book screen, or refreshes one already open on the same book.
 *
 * @param bookId   the {@code spell_book} id
 * @param hand     the hand holding it (InteractionHand ordinal), echoed back in a chapter pick
 * @param chapters in reading order
 */
@NullMarked
public record SpellBookOpenS2CPayload(String bookId, int hand, List<Chapter> chapters) implements CustomPacketPayload {

    /** Known already. */
    public static final int KNOWN = 0;
    /** May be studied now. */
    public static final int OPEN = 1;
    /** Refused by a gate; {@code reason} says why. */
    public static final int LOCKED = 2;

    /**
     * @param spellId    registered spell id
     * @param state      {@link #KNOWN}, {@link #OPEN} or {@link #LOCKED}
     * @param difficulty TraceDifficulty ordinal, so the page can say how hard the gesture is
     * @param reason     a refusal as serialised Component JSON ({@link SpellTraceResultS2CPayload#encodeDetail}),
     *                   empty unless locked
     */
    public record Chapter(String spellId, int state, int difficulty, String reason) {}

    public static final Type<SpellBookOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_book_open"));

    public static final StreamCodec<ByteBuf, SpellBookOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellBookOpenS2CPayload decode(ByteBuf buf) {
            String book = PacketCodecUtils.readString(buf);
            int hand = buf.readByte();
            int n = buf.readShort();
            List<Chapter> chapters = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                chapters.add(new Chapter(PacketCodecUtils.readString(buf), buf.readByte(), buf.readByte(),
                        PacketCodecUtils.readString(buf)));
            }
            return new SpellBookOpenS2CPayload(book, hand, chapters);
        }

        @Override
        public void encode(ByteBuf buf, SpellBookOpenS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.bookId);
            buf.writeByte(pkt.hand);
            buf.writeShort(pkt.chapters.size());
            for (Chapter c : pkt.chapters) {
                PacketCodecUtils.writeString(buf, c.spellId());
                buf.writeByte(c.state());
                buf.writeByte(c.difficulty());
                PacketCodecUtils.writeString(buf, c.reason());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** {@code reason} as the sentence to show. */
    public static net.minecraft.network.chat.Component reasonText(Chapter chapter) {
        if (chapter.reason().isEmpty()) {
            return net.minecraft.network.chat.Component.empty();
        }
        try {
            return net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE,
                            com.google.gson.JsonParser.parseString(chapter.reason()))
                    .result().orElse(net.minecraft.network.chat.Component.literal(chapter.reason()));
        } catch (RuntimeException e) {
            return net.minecraft.network.chat.Component.literal(chapter.reason());
        }
    }
}
