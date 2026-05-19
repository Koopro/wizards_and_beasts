package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record ImperioResistS2CPayload(boolean success, float resistanceProgress) implements CustomPacketPayload {
    public static final Type<ImperioResistS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "imperio_resist"));

    public static final StreamCodec<ByteBuf, ImperioResistS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ImperioResistS2CPayload::success,
            ByteBufCodecs.FLOAT, ImperioResistS2CPayload::resistanceProgress,
            ImperioResistS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ImperioResistS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            String msg = pkt.success
                    ? "\u00A76You throw off the Imperius Curse!"
                    : "\u00A7eYou struggle against the Imperius Curse... (" + String.format("%.0f%%", pkt.resistanceProgress * 100f) + ")";
            mc.player.displayClientMessage(Component.literal(msg), true);
        });
    }
}
