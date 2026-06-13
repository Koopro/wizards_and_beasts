package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/** Sent server→client whenever a cast attempt is blocked (cooldown, GCD, Gamp, fizzle). */
@NullMarked
public record SpellDeniedS2CPayload() implements CustomPacketPayload {

    public static final Type<SpellDeniedS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_denied"));

    public static final StreamCodec<ByteBuf, SpellDeniedS2CPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(SpellDeniedS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SpellDeniedS2CPayload());
    }
}
