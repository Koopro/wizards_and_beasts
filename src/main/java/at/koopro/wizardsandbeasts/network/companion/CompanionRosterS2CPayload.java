package at.koopro.wizardsandbeasts.network.companion;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * S→C: the owner's companion roster, for the Beasts tab. Sent on login and, batched per server tick, whenever
 * an entry changes ({@code Companions#flushRosters}) — never on a timer, and only to the owner.
 */
@NullMarked
public record CompanionRosterS2CPayload(CompanionRoster roster) implements CustomPacketPayload {

    public static final Type<CompanionRosterS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "companion_roster"));

    public static final StreamCodec<ByteBuf, CompanionRosterS2CPayload> STREAM_CODEC =
            CompanionRoster.STREAM_CODEC.map(CompanionRosterS2CPayload::new, CompanionRosterS2CPayload::roster);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new CompanionRosterS2CPayload(player.getData(ModAttachments.COMPANION_ROSTER.get())));
    }
}
