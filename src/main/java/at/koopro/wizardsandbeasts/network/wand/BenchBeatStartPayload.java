package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import at.koopro.wizardsandbeasts.wand.making.CoreMaking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: an act at the bench has begun; play its beat. Carries the seed and every number the beat is built
 * from ({@link BenchCraft.Odds}), so the client builds exactly the beat the server will score.
 */
public record BenchBeatStartPayload(BlockPos pos, long seed, BenchCraft.Odds odds) implements CustomPacketPayload {

    public static final Type<BenchBeatStartPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bench_beat_start"));

    public static final StreamCodec<FriendlyByteBuf, BenchBeatStartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeLong(p.seed);
                BenchCraft.Odds o = p.odds;
                buf.writeVarInt(o.act().ordinal());
                buf.writeFloat(o.hardness());
                buf.writeFloat(o.ease());
                buf.writeVarInt(o.window());
                buf.writeVarInt(o.forgive());
                buf.writeFloat(o.band());
                buf.writeFloat(o.risk());
                buf.writeVarInt(o.pulse().tempo());
                buf.writeFloat(o.pulse().jitter());
                buf.writeFloat(o.pulse().drift());
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                long seed = buf.readLong();
                int act = buf.readVarInt();
                BenchCraft.Act[] acts = BenchCraft.Act.values();
                BenchCraft.Odds odds = new BenchCraft.Odds(acts[Math.floorMod(act, acts.length)],
                        buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readFloat(), new CoreMaking.Pulse(buf.readVarInt(), buf.readFloat(), buf.readFloat()));
                return new BenchBeatStartPayload(pos, seed, odds);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BenchBeatStartPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> at.koopro.wizardsandbeasts.client.wand.BenchBeatClient.start(pkt.pos, pkt.seed, pkt.odds));
    }
}
