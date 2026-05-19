package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import net.minecraft.world.item.ItemStack;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectTrialWandPayload(int containerId, int slotIndex) implements CustomPacketPayload {

    public static final Type<SelectTrialWandPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "select_trial_wand"));

    public static final StreamCodec<ByteBuf, SelectTrialWandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SelectTrialWandPayload::containerId,
            ByteBufCodecs.VAR_INT, SelectTrialWandPayload::slotIndex,
            SelectTrialWandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectTrialWandPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof OllivanderTrialMenu menu) || menu.containerId != pkt.containerId) {
                return;
            }
            if (pkt.slotIndex < 0 || pkt.slotIndex >= 3) {
                return;
            }
            menu.setSelectedIndex(pkt.slotIndex);
            ItemStack trial = menu.createTrialStack(pkt.slotIndex, false);
            float score = WandResonanceSystem.computeResonance(player, trial, player.registryAccess());
            menu.setResonanceScore(pkt.slotIndex, score);
        });
    }
}
