package at.koopro.wizardsandbeasts.ministry.licence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * What is written on a Ministry licence scroll.
 *
 * <p>The whole document is one data component, not six, because every field on it is only meaningful
 * beside the others: a rank with no type endorses nothing, an expiry with no holder expires for
 * nobody. It is also what makes the client presentation free — the scroll replicates, so the seal,
 * the tooltip and the screen all read the same record the server wrote.
 *
 * <p><b>{@code forged} is stored, not inferred.</b> A forgery has to be indistinguishable from the
 * real thing on inspection or it is not a forgery; what separates them is a flag only the server ever
 * reads, and a per-interaction chance that the flag is noticed. See {@link LicenceForgery}.
 *
 * @param type      what the licence permits
 * @param rank      endorsement level, 0–{@link LicenseType#MAX_RANK}
 * @param issuedTo  the wizard named on it — a licence in someone else's hands is not their licence
 * @param expiryTick game time the licence lapses at; {@link #NEVER_EXPIRES} for a permanent warrant
 * @param revoked   struck off by the Ministry; nothing restores this but a new licence
 * @param forged    written by somebody other than the Ministry
 */
@NullMarked
public record LicenseData(
        LicenseType type,
        int rank,
        UUID issuedTo,
        long expiryTick,
        boolean revoked,
        boolean forged) {

    /** Expiry value meaning the licence never lapses. */
    public static final long NEVER_EXPIRES = -1L;

    public LicenseData {
        rank = Math.max(0, Math.min(LicenseType.MAX_RANK, rank));
        if (expiryTick < 0L) {
            expiryTick = NEVER_EXPIRES;
        }
    }

    public static final Codec<LicenseData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            LicenseType.CODEC.fieldOf("type").forGetter(LicenseData::type),
            Codec.INT.optionalFieldOf("rank", 0).forGetter(LicenseData::rank),
            // LENIENT_CODEC, not CODEC: the strict one is the int-array form, and a licence is a
            // document people write by hand in /give and /data commands. This reads the dashed string
            // as well, so a hand-authored scroll does not silently fail to parse.
            UUIDUtil.LENIENT_CODEC.fieldOf("issued_to").forGetter(LicenseData::issuedTo),
            // Optional so a scroll written before a field existed loads as a plain permanent licence
            // rather than failing to parse and silently becoming a blank scroll.
            Codec.LONG.optionalFieldOf("expiry_tick", NEVER_EXPIRES).forGetter(LicenseData::expiryTick),
            Codec.BOOL.optionalFieldOf("revoked", false).forGetter(LicenseData::revoked),
            Codec.BOOL.optionalFieldOf("forged", false).forGetter(LicenseData::forged)
    ).apply(inst, LicenseData::new));

    public static final StreamCodec<ByteBuf, LicenseData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LicenseData decode(ByteBuf buf) {
            LicenseType type = LicenseType.values()[
                    Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), LicenseType.values().length)];
            int rank = ByteBufCodecs.VAR_INT.decode(buf);
            UUID issuedTo = UUIDUtil.STREAM_CODEC.decode(buf);
            long expiry = ByteBufCodecs.VAR_LONG.decode(buf);
            byte flags = buf.readByte();
            return new LicenseData(type, rank, issuedTo, expiry,
                    (flags & REVOKED_FLAG) != 0, (flags & FORGED_FLAG) != 0);
        }

        @Override
        public void encode(ByteBuf buf, LicenseData value) {
            ByteBufCodecs.VAR_INT.encode(buf, value.type().ordinal());
            ByteBufCodecs.VAR_INT.encode(buf, value.rank());
            UUIDUtil.STREAM_CODEC.encode(buf, value.issuedTo());
            ByteBufCodecs.VAR_LONG.encode(buf, value.expiryTick());
            int flags = (value.revoked() ? REVOKED_FLAG : 0) | (value.forged() ? FORGED_FLAG : 0);
            buf.writeByte(flags);
        }
    };

    private static final int REVOKED_FLAG = 0b01;
    private static final int FORGED_FLAG = 0b10;

    /** A fresh, permanent, genuine licence at rank 0. */
    public static LicenseData issue(LicenseType type, UUID holder) {
        return new LicenseData(type, 0, holder, NEVER_EXPIRES, false, false);
    }

    public boolean isPermanent() {
        return expiryTick == NEVER_EXPIRES;
    }

    public boolean hasExpired(long gameTime) {
        return !isPermanent() && gameTime >= expiryTick;
    }

    /**
     * Whether this document actually authorises {@code holder} right now.
     *
     * <p>Forgery is deliberately <em>not</em> checked. A convincing forgery works — that is the whole
     * point of one — right up until an official looks at it too closely and revokes it. Reading
     * {@code forged} here would make every forgery useless on the first use and the 15% detection roll
     * pointless.
     */
    public boolean authorises(UUID holder, long gameTime) {
        return !revoked && !hasExpired(gameTime) && issuedTo.equals(holder);
    }

    public LicenseData revokedCopy() {
        return new LicenseData(type, rank, issuedTo, expiryTick, true, forged);
    }

    public LicenseData withRank(int value) {
        return new LicenseData(type, value, issuedTo, expiryTick, revoked, forged);
    }

    public LicenseData forgedCopy() {
        return new LicenseData(type, rank, issuedTo, expiryTick, revoked, true);
    }
}
