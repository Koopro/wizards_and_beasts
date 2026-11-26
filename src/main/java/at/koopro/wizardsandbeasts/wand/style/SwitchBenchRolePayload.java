package at.koopro.wizardsandbeasts.wand.style;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.bench.BenchRole;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Switches an open bench to another role ({@link BenchRole}: make, mend, restyle): the player pressed one of the
 * screen's leather tags. The server opens that role's menu on the same bench; what lay in the role being closed goes
 * back to the player, as when any bench screen closes.
 *
 * @param role a {@link BenchRole} ordinal
 */
public record SwitchBenchRolePayload(int containerId, int role) implements CustomPacketPayload {

    public static final Type<SwitchBenchRolePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "switch_bench_role"));

    public static final StreamCodec<ByteBuf, SwitchBenchRolePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SwitchBenchRolePayload::containerId,
            ByteBufCodecs.VAR_INT, SwitchBenchRolePayload::role,
            SwitchBenchRolePayload::new);

    public SwitchBenchRolePayload(int containerId, BenchRole role) {
        this(containerId, role.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwitchBenchRolePayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.containerMenu.containerId != pkt.containerId()
                    || pkt.role() < 0 || pkt.role() >= BenchRole.values().length) {
                return;
            }
            WandmakersBenchBlockEntity bench = BenchRole.benchOf(player.containerMenu);
            if (bench != null && player.containerMenu.stillValid(player)) {
                BenchRole.values()[pkt.role()].open(player, bench);
            }
        });
    }
}
