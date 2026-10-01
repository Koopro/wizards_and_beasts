package at.koopro.wizardsandbeasts.network.visual;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.beam.ClientBeamVisuals;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server → client: the server's beam visual overrides (spell key → property id → text), the whole table.
 *
 * <p>Sent on join, on {@code /reload} and after an administrator changes a beam — never per tick. A few dozen short
 * strings at most. The client composes each spell's look from its authored default and these overrides; entries it
 * cannot parse are dropped there, so a hostile or outdated server can at worst leave a beam at its default.
 */
@NullMarked
public record BeamVisualSyncS2CPayload(Map<String, Map<String, String>> overrides) implements CustomPacketPayload {

    private static final int MAX_SPELLS = 64;
    private static final int MAX_PROPERTIES = 64;

    public static final Type<BeamVisualSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_visual_sync"));

    public static final StreamCodec<ByteBuf, BeamVisualSyncS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                int spells = Math.min(payload.overrides.size(), MAX_SPELLS);
                buf.writeInt(spells);
                payload.overrides.entrySet().stream().limit(spells).forEach(spell -> {
                    PacketCodecUtils.writeString(buf, spell.getKey());
                    int count = Math.min(spell.getValue().size(), MAX_PROPERTIES);
                    buf.writeInt(count);
                    spell.getValue().entrySet().stream().limit(count).forEach(entry -> {
                        PacketCodecUtils.writeString(buf, entry.getKey());
                        PacketCodecUtils.writeString(buf, entry.getValue());
                    });
                });
            },
            buf -> {
                int spells = PacketCodecUtils.readBoundedCount(buf, MAX_SPELLS, "beam-visual-spells");
                Map<String, Map<String, String>> out = new LinkedHashMap<>();
                for (int i = 0; i < spells; i++) {
                    String spell = PacketCodecUtils.readString(buf);
                    int count = PacketCodecUtils.readBoundedCount(buf, MAX_PROPERTIES, "beam-visual-properties");
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int j = 0; j < count; j++) {
                        values.put(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf));
                    }
                    out.put(spell, Map.copyOf(values));
                }
                return new BeamVisualSyncS2CPayload(Map.copyOf(out));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, Map<String, Map<String, String>> overrides) {
        PacketDistributor.sendToPlayer(player, new BeamVisualSyncS2CPayload(overrides));
    }

    /** Client handler; the body only runs on a client, so naming a client class here is dist-safe. */
    public static void handleClient(BeamVisualSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBeamVisuals.accept(payload.overrides()));
    }
}
