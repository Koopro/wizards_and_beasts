package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hot-path spell-data delta sent on every successful cast. Carries only the
 * fields the cast itself mutates — the post-cast cooldown expiry tick and the
 * new lifetime cast count for the cast spell — so {@link SpellDataSyncS2CPayload}
 * (full snapshot) can be reserved for slow events: login, learn-spell,
 * loadout-edit, admin commands.
 */
public record SpellDataDeltaS2CPayload(
        String spellId,
        long cooldownExpiryTick,
        int newCastCount,
        int newSuccessfulHits,
        long globalCooldownEndTick) implements CustomPacketPayload {

    public static final Type<SpellDataDeltaS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_data_delta"));

    public static final StreamCodec<ByteBuf, SpellDataDeltaS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellDataDeltaS2CPayload decode(ByteBuf buf) {
            String id = PacketCodecUtils.readString(buf);
            long expiry = buf.readLong();
            int casts = buf.readInt();
            int successfulHits = buf.readInt();
            long globalCooldown = buf.readLong();
            return new SpellDataDeltaS2CPayload(id, expiry, casts, successfulHits, globalCooldown);
        }

        @Override
        public void encode(ByteBuf buf, SpellDataDeltaS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.spellId);
            buf.writeLong(pkt.cooldownExpiryTick);
            buf.writeInt(pkt.newCastCount);
            buf.writeInt(pkt.newSuccessfulHits);
            buf.writeLong(pkt.globalCooldownEndTick);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player,
                              String spellId,
                              long expiryTick,
                              int newCastCount,
                              int newSuccessfulHits,
                              long globalCooldownEndTick) {
        PacketDistributor.sendToPlayer(player, new SpellDataDeltaS2CPayload(
                spellId,
                expiryTick,
                newCastCount,
                newSuccessfulHits,
                globalCooldownEndTick));
    }
}
