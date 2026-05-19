package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChooseTrialWandPayload(int containerId, int slotIndex) implements CustomPacketPayload {

    public static final Type<ChooseTrialWandPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "choose_trial_wand"));

    public static final StreamCodec<ByteBuf, ChooseTrialWandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseTrialWandPayload::containerId,
            ByteBufCodecs.VAR_INT, ChooseTrialWandPayload::slotIndex,
            ChooseTrialWandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChooseTrialWandPayload pkt, IPayloadContext ctx) {
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
            float thresh = WandResonanceConfigLoader.getConfig(player.registryAccess()).matchThreshold();
            if (menu.getResonanceScore(pkt.slotIndex) < thresh) {
                return;
            }
            ItemStack wand = menu.createTrialStack(pkt.slotIndex, true);
            float score = WandResonanceSystem.computeResonance(player, wand, player.registryAccess());
            WandResonanceSystem.applyResonance(player, wand, score, player.registryAccess());
            if (!player.addItem(wand)) {
                player.drop(wand, false);
            }
            player.closeContainer();
        });
    }
}
