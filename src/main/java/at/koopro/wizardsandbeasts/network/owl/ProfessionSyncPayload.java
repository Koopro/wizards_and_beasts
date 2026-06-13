package at.koopro.wizardsandbeasts.network.owl;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.owl.Profession;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** S→C: syncs the player's chosen profession (or null if none chosen yet). */
public record ProfessionSyncPayload(@Nullable Profession profession) implements CustomPacketPayload {
    private static final int NONE = -1;

    public static final Type<ProfessionSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "profession_sync"));

    public static final StreamCodec<ByteBuf, ProfessionSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ProfessionSyncPayload decode(ByteBuf buf) {
            int ord = buf.readByte();
            Profession[] profs = Profession.values();
            Profession prof = (ord >= 0 && ord < profs.length) ? profs[ord] : null;
            return new ProfessionSyncPayload(prof);
        }

        @Override
        public void encode(ByteBuf buf, ProfessionSyncPayload payload) {
            buf.writeByte(payload.profession() != null ? payload.profession().ordinal() : NONE);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
