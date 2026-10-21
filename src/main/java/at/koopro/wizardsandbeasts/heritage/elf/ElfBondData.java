package at.koopro.wizardsandbeasts.heritage.elf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Whom a bound house-elf serves ({@link HouseElfBond}). Held on the elf, never on the master: an elf has one master,
 * a master may keep several elves, and the elf's own record is what survives the master logging out.
 *
 * @param master     the master's UUID, empty while the elf serves no one
 * @param masterName the master's name when the bond was made, for the character sheet while they are offline
 * @param sinceDay   the in-game day the bond was made
 * @param summons    summonses answered, lifetime
 */
@NullMarked
public record ElfBondData(Optional<UUID> master, String masterName, long sinceDay, int summons) {

    public static final ElfBondData NONE = new ElfBondData(Optional.empty(), "", 0L, 0);

    public static final Codec<ElfBondData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("master").forGetter(ElfBondData::master),
            Codec.STRING.optionalFieldOf("master_name", "").forGetter(ElfBondData::masterName),
            Codec.LONG.optionalFieldOf("since_day", 0L).forGetter(ElfBondData::sinceDay),
            Codec.INT.optionalFieldOf("summons", 0).forGetter(ElfBondData::summons)
    ).apply(i, ElfBondData::new));

    public boolean bound() {
        return master.isPresent();
    }

    public boolean servedBy(UUID player) {
        return master.isPresent() && master.get().equals(player);
    }

    public static ElfBondData to(UUID master, String name, long day) {
        return new ElfBondData(Optional.of(master), name, day, 0);
    }

    public ElfBondData answered() {
        return new ElfBondData(master, masterName, sinceDay, summons + 1);
    }
}
