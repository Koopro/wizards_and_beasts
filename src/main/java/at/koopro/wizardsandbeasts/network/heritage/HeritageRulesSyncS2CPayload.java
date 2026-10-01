package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.heritage.HeritageRulesClient;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRule;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server → every client: the whole heritage rule state. Sent on join and after any change, to everyone, because
 * the onboarding screen every new player sees reads it. A full snapshot — at most one entry per heritage.
 */
@NullMarked
public record HeritageRulesSyncS2CPayload(Map<String, HeritageRule> rules) implements CustomPacketPayload {

    private static final int MAX_RULES = 256;
    private static final int SELECTABLE = 1;
    private static final int TRANSFORMATION = 1 << 1;

    public static final Type<HeritageRulesSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage_rules_sync"));

    public static final StreamCodec<ByteBuf, HeritageRulesSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HeritageRulesSyncS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, MAX_RULES, "heritage-rules");
            Map<String, HeritageRule> rules = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                String id = PacketCodecUtils.readString(buf);
                int flags = buf.readUnsignedByte();
                rules.put(id, new HeritageRule(
                        (flags & SELECTABLE) != 0 ? Optional.of(buf.readBoolean()) : Optional.empty(),
                        (flags & TRANSFORMATION) != 0 ? Optional.of(buf.readBoolean()) : Optional.empty()));
            }
            return new HeritageRulesSyncS2CPayload(Map.copyOf(rules));
        }

        @Override
        public void encode(ByteBuf buf, HeritageRulesSyncS2CPayload payload) {
            int count = Math.min(payload.rules.size(), MAX_RULES);
            buf.writeInt(count);
            int written = 0;
            for (Map.Entry<String, HeritageRule> entry : payload.rules.entrySet()) {
                if (written++ >= count) {
                    break;
                }
                HeritageRule rule = entry.getValue();
                PacketCodecUtils.writeString(buf, entry.getKey());
                buf.writeByte((rule.selectable().isPresent() ? SELECTABLE : 0)
                        | (rule.transformation().isPresent() ? TRANSFORMATION : 0));
                rule.selectable().ifPresent(buf::writeBoolean);
                rule.transformation().ifPresent(buf::writeBoolean);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(HeritageRulesSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HeritageRulesClient.accept(payload.rules()));
    }
}
