package at.koopro.wizardsandbeasts.wand.allegiance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a wand remembers about the wizards who have held its allegiance — the history beside the two facts
 * the cast path reads directly ({@code WAND_MASTER}, who it answers to, and {@code WAND_ALLEGIANCE_SCORE},
 * how strongly).
 *
 * <p>Stored on the stack, so it travels with the wand through chests, deaths and dropped items and survives a
 * restart with nothing else to keep in step.
 *
 * @param firstMaster       the first wizard it ever chose; an ash wand never serves anyone else as well
 * @param formerMaster      the master it served before the current one, if it was won
 * @param challenger        the wizard who has most recently defeated its master
 * @param challengerWins    how many times in a row that challenger has done so since the master last cast
 * @param lastMasterUseTick game time of the master's last successful cast, or 0 if unknown
 */
public record WandBondHistory(
        @Nullable UUID firstMaster,
        @Nullable UUID formerMaster,
        @Nullable UUID challenger,
        int challengerWins,
        long lastMasterUseTick) {

    public static final WandBondHistory EMPTY = new WandBondHistory(null, null, null, 0, 0L);

    /**
     * Absent ids are {@code Optional} inside the codec and {@code null} on the record: DataFixerUpper refuses a
     * {@code null} passed through {@code xmap}, so an unwritten or unreadable id would otherwise fail the whole
     * stack's decode.
     */
    public static final Codec<WandBondHistory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            uuidField("first_master").forGetter(h -> Optional.ofNullable(h.firstMaster())),
            uuidField("former_master").forGetter(h -> Optional.ofNullable(h.formerMaster())),
            uuidField("challenger").forGetter(h -> Optional.ofNullable(h.challenger())),
            Codec.INT.optionalFieldOf("challenger_wins", 0).forGetter(WandBondHistory::challengerWins),
            Codec.LONG.optionalFieldOf("last_master_use_tick", 0L).forGetter(WandBondHistory::lastMasterUseTick)
    ).apply(instance, (first, former, challenger, wins, lastUse) ->
            new WandBondHistory(first.orElse(null), former.orElse(null), challenger.orElse(null), wins, lastUse)));

    public static final StreamCodec<ByteBuf, WandBondHistory> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WandBondHistory decode(ByteBuf buf) {
            return new WandBondHistory(readUuid(buf), readUuid(buf), readUuid(buf),
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT.decode(buf),
                    net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, WandBondHistory value) {
            writeUuid(buf, value.firstMaster());
            writeUuid(buf, value.formerMaster());
            writeUuid(buf, value.challenger());
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, value.challengerWins());
            net.minecraft.network.codec.ByteBufCodecs.VAR_LONG.encode(buf, value.lastMasterUseTick());
        }
    };

    public WandBondHistory {
        challengerWins = Math.max(0, challengerWins);
        if (challenger == null) {
            challengerWins = 0;
        }
    }

    /** Records the first master, once. Later calls leave it alone. */
    public WandBondHistory withFirstMasterIfAbsent(UUID master) {
        return firstMaster != null ? this : new WandBondHistory(master, formerMaster, challenger, challengerWins, lastMasterUseTick);
    }

    /** A defeat of the master by {@code victor}: the tally continues for the same challenger and restarts for a new one. */
    public WandBondHistory withDefeatBy(UUID victor) {
        int wins = Objects.equals(challenger, victor) ? challengerWins + 1 : 1;
        return new WandBondHistory(firstMaster, formerMaster, victor, wins, lastMasterUseTick);
    }

    /** The master has reasserted itself with a successful cast: no challenge stands. */
    public WandBondHistory withMasterCast(long gameTick) {
        return new WandBondHistory(firstMaster, formerMaster, null, 0, gameTick);
    }

    /** The wand has been won: the old master becomes the former one, and the new one has not cast with it yet. */
    public WandBondHistory afterTransferFrom(@Nullable UUID oldMaster) {
        return new WandBondHistory(firstMaster, oldMaster, null, 0, 0L);
    }

    private static com.mojang.serialization.MapCodec<Optional<UUID>> uuidField(String name) {
        return Codec.STRING.optionalFieldOf(name).xmap(
                o -> o.flatMap(raw -> Optional.ofNullable(parseUuid(raw))),
                u -> u.map(UUID::toString));
    }

    private static @Nullable UUID parseUuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static @Nullable UUID readUuid(ByteBuf buf) {
        return buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
    }

    private static void writeUuid(ByteBuf buf, @Nullable UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    }
}
