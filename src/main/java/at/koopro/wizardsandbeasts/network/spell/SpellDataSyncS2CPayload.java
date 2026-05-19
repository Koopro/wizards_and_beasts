package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Full {@link PlayerSpellData} snapshot pushed from server to client. The wire
 * format is intentionally <em>typed and decoupled</em> from the on-disk NBT
 * format: the server may bump {@link PlayerSpellData#CURRENT_VERSION} without
 * forcing a network protocol bump, and the client never sees raw NBT.
 * <p>
 * For per-cast updates (cooldown set + cast-count increment) prefer the
 * smaller {@link SpellDataDeltaS2CPayload} so we don't re-serialize the entire
 * known-spells set on every wand release.
 */
public record SpellDataSyncS2CPayload(
        int syncVersion,
        Set<String> knownSpells,
        String[] loadout,
        int activeSlot,
        Map<String, Long> cooldowns,
        Map<String, Integer> castCounts,
        Map<String, Integer> successfulHits,
        Map<String, Float> spellProficiencies,
        Map<String, Integer> rejectCounts,
        int syncCorrections) implements CustomPacketPayload {
    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<SpellDataSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_data_sync"));

    public static final StreamCodec<ByteBuf, SpellDataSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellDataSyncS2CPayload decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            int knownCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_KNOWN_SPELLS, "known-spells");
            Set<String> known = new HashSet<>(Math.max(8, knownCount));
            for (int i = 0; i < knownCount; i++) {
                known.add(PacketCodecUtils.readString(buf));
            }

            String[] loadout = new String[PlayerSpellData.LOADOUT_SIZE];
            for (int i = 0; i < loadout.length; i++) {
                if (buf.readBoolean()) {
                    loadout[i] = PacketCodecUtils.readString(buf);
                }
            }

            int activeSlot = buf.readInt();

            int cdCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_COOLDOWNS, "cooldowns");
            Map<String, Long> cooldowns = new HashMap<>(Math.max(8, cdCount));
            for (int i = 0; i < cdCount; i++) {
                String id = PacketCodecUtils.readString(buf);
                long expiry = buf.readLong();
                cooldowns.put(id, expiry);
            }

            int ccCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_CAST_COUNTS, "cast-counts");
            Map<String, Integer> castCounts = new HashMap<>(Math.max(8, ccCount));
            for (int i = 0; i < ccCount; i++) {
                String id = PacketCodecUtils.readString(buf);
                int casts = PacketCodecUtils.clampNonNegative(buf.readInt());
                castCounts.put(id, casts);
            }

            int shCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_CAST_COUNTS, "successful-hits");
            Map<String, Integer> successfulHits = new HashMap<>(Math.max(8, shCount));
            for (int i = 0; i < shCount; i++) {
                String id = PacketCodecUtils.readString(buf);
                int hits = PacketCodecUtils.clampNonNegative(buf.readInt());
                successfulHits.put(id, hits);
            }
            int profCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_CAST_COUNTS, "spell-proficiencies");
            Map<String, Float> spellProficiencies = new HashMap<>(Math.max(8, profCount));
            for (int i = 0; i < profCount; i++) {
                String id = PacketCodecUtils.readString(buf);
                float value = Math.max(0.0f, Math.min(1.0f, buf.readFloat()));
                spellProficiencies.put(id, value);
            }

            int rejectCount = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_CAST_COUNTS, "reject-counts");
            Map<String, Integer> rejectCounts = new HashMap<>(Math.max(8, rejectCount));
            for (int i = 0; i < rejectCount; i++) {
                String id = PacketCodecUtils.readString(buf);
                int count = PacketCodecUtils.clampNonNegative(buf.readInt());
                rejectCounts.put(id, count);
            }
            int syncCorrections = PacketCodecUtils.clampNonNegative(buf.readInt());

            return new SpellDataSyncS2CPayload(
                    syncVersion,
                    known,
                    loadout,
                    activeSlot,
                    cooldowns,
                    castCounts,
                    successfulHits,
                    spellProficiencies,
                    rejectCounts,
                    syncCorrections);
        }

        @Override
        public void encode(ByteBuf buf, SpellDataSyncS2CPayload pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            buf.writeInt(pkt.knownSpells.size());
            for (String s : pkt.knownSpells) {
                PacketCodecUtils.writeString(buf, s);
            }

            for (int i = 0; i < PlayerSpellData.LOADOUT_SIZE; i++) {
                String slot = i < pkt.loadout.length ? pkt.loadout[i] : null;
                if (slot == null) {
                    buf.writeBoolean(false);
                } else {
                    buf.writeBoolean(true);
                    PacketCodecUtils.writeString(buf, slot);
                }
            }

            buf.writeInt(pkt.activeSlot);

            buf.writeInt(pkt.cooldowns.size());
            for (Map.Entry<String, Long> e : pkt.cooldowns.entrySet()) {
                PacketCodecUtils.writeString(buf, e.getKey());
                buf.writeLong(e.getValue());
            }

            buf.writeInt(pkt.castCounts.size());
            for (Map.Entry<String, Integer> e : pkt.castCounts.entrySet()) {
                PacketCodecUtils.writeString(buf, e.getKey());
                buf.writeInt(e.getValue());
            }

            buf.writeInt(pkt.successfulHits.size());
            for (Map.Entry<String, Integer> e : pkt.successfulHits.entrySet()) {
                PacketCodecUtils.writeString(buf, e.getKey());
                buf.writeInt(e.getValue());
            }
            buf.writeInt(pkt.spellProficiencies.size());
            for (Map.Entry<String, Float> e : pkt.spellProficiencies.entrySet()) {
                PacketCodecUtils.writeString(buf, e.getKey());
                buf.writeFloat(Math.max(0.0f, Math.min(1.0f, e.getValue())));
            }

            buf.writeInt(pkt.rejectCounts.size());
            for (Map.Entry<String, Integer> e : pkt.rejectCounts.entrySet()) {
                PacketCodecUtils.writeString(buf, e.getKey());
                buf.writeInt(e.getValue());
            }
            buf.writeInt(pkt.syncCorrections);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Builds a snapshot packet from server-side player state. */
    public static SpellDataSyncS2CPayload of(PlayerSpellData data) {
        return new SpellDataSyncS2CPayload(
                NEXT_SYNC_VERSION.incrementAndGet(),
                new HashSet<>(data.getKnownSpells()),
                data.getLoadout().clone(),
                data.getActiveSlot(),
                new HashMap<>(data.getCooldowns()),
                new HashMap<>(data.getCastCounts()),
                new HashMap<>(data.getSuccessfulHitCounts()),
                new HashMap<>(data.getSpellProficiencies()),
                new HashMap<>(data.getRejectCounts()),
                data.getSyncCorrections());
    }

    public static void handleClient(SpellDataSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSpellDataState.applyFullSync(pkt));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        PacketDistributor.sendToPlayer(player, of(data));
    }
}
