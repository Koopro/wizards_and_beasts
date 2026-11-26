package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.bench.BenchMaking;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: how the player played an act's beat. Only the ticks the use key was pressed on ({@code taps}) and
 * held on ({@code held}, from the end of the lead); the server rebuilds the beat from its own seed and scores them.
 */
public record BenchBeatResultPayload(BlockPos pos, long seed, int[] taps, boolean[] held) implements CustomPacketPayload {

    /** More taps than any beat has beats, twice over, is noise. */
    private static final int MAX_TAPS = 64;

    public static final Type<BenchBeatResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bench_beat_result"));

    public static final StreamCodec<FriendlyByteBuf, BenchBeatResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeLong(p.seed);
                buf.writeVarIntArray(p.taps.length <= MAX_TAPS ? p.taps : java.util.Arrays.copyOf(p.taps, MAX_TAPS));
                byte[] bits = new byte[Math.min(p.held.length, BenchCraft.BORE_TICKS)];
                for (int i = 0; i < bits.length; i++) {
                    bits[i] = (byte) (p.held[i] ? 1 : 0);
                }
                buf.writeByteArray(bits);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                long seed = buf.readLong();
                int[] taps = buf.readVarIntArray(MAX_TAPS);
                byte[] bits = buf.readByteArray(BenchCraft.BORE_TICKS);
                boolean[] held = new boolean[bits.length];
                for (int i = 0; i < bits.length; i++) {
                    held[i] = bits[i] != 0;
                }
                return new BenchBeatResultPayload(pos, seed, taps, held);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BenchBeatResultPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.level().isLoaded(pkt.pos)
                    && player.level().getBlockEntity(pkt.pos) instanceof WandmakersBenchBlockEntity bench) {
                BenchMaking.resolve(bench, player, pkt.seed, pkt.taps, pkt.held);
            }
        });
    }
}
