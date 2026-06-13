package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/** Tells the Imperius caster which victim is bound for command UI; all-zero UUID clears. */
public record ImperioVictimBoundS2CPayload(UUID victimUuid) implements CustomPacketPayload {

    public static final Type<ImperioVictimBoundS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "imperio_victim_bound"));

    public static final StreamCodec<ByteBuf, ImperioVictimBoundS2CPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ImperioVictimBoundS2CPayload::victimUuid,
            ImperioVictimBoundS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
