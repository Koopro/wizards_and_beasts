package at.koopro.wizardsandbeasts.wand.style;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Switches an open bench between refining and restyling a wand. The server opens the other role on the same bench;
 * the wand left in the role being closed goes back to the player as when any bench screen closes.
 *
 * @param toRestyle true from the refine screen, false to go back from the restyle screen
 */
public record SwitchBenchRolePayload(int containerId, boolean toRestyle) implements CustomPacketPayload {

    public static final Type<SwitchBenchRolePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "switch_bench_role"));

    public static final StreamCodec<ByteBuf, SwitchBenchRolePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SwitchBenchRolePayload::containerId,
            ByteBufCodecs.BOOL, SwitchBenchRolePayload::toRestyle,
            SwitchBenchRolePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwitchBenchRolePayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.containerMenu.containerId != pkt.containerId()) {
                return;
            }
            if (pkt.toRestyle() && player.containerMenu instanceof WandRefineMenu refine
                    && refine.stillValid(player)) {
                WandRestyleMenu.open(player, refine.bench());
            } else if (!pkt.toRestyle() && player.containerMenu instanceof WandRestyleMenu restyle
                    && restyle.stillValid(player)) {
                WandmakersBenchBlockEntity bench = restyle.bench();
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WandRefineMenu(id, inventory, bench),
                        Component.translatable("gui.wizards_and_beasts.wand_refine")),
                        buf -> buf.writeBlockPos(bench.getBlockPos()));
            }
        });
    }
}
