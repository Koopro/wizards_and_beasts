package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How much each wizard without a Magical Commerce licence has taken across lawful trader counters this in-game
 * week. Past {@code ministryUnlicensedSalesKnutsPerWeek} the counter will not buy: trading at volume is a business,
 * and a business needs its papers. A refusal, never an offence — the licence is the legal alternative, sold at the
 * Ministry's licence office.
 *
 * <p>World data keyed by UUID, so a restart does not reset the week and a player's sales follow them between
 * counters. One entry per seller, overwritten when a new week begins: it never grows past the number of sellers.
 */
@NullMarked
public final class CommerceAllowance extends SavedData {

    /** Ticks in one in-game week. */
    public static final long WEEK_TICKS = 7L * 24000L;

    private record Week(long week, long knuts) {
        static final Codec<Week> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("week").forGetter(Week::week),
                Codec.LONG.fieldOf("knuts").forGetter(Week::knuts)
        ).apply(instance, Week::new));
    }

    public static final SavedDataType<CommerceAllowance> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_commerce_allowance",
            CommerceAllowance::new,
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Week.CODEC).xmap(CommerceAllowance::new, data -> data.sales));

    private final Map<UUID, Week> sales;

    private CommerceAllowance() {
        this.sales = new HashMap<>();
    }

    private CommerceAllowance(Map<UUID, Week> stored) {
        this.sales = new HashMap<>(stored);
    }

    public static CommerceAllowance get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** What {@code seller} has taken at counters in the week containing {@code now}. */
    public long takenThisWeek(UUID seller, long now) {
        Week week = sales.get(seller);
        return week == null || week.week() != now / WEEK_TICKS ? 0L : week.knuts();
    }

    public void record(UUID seller, long now, long knuts) {
        if (knuts <= 0L) {
            return;
        }
        sales.put(seller, new Week(now / WEEK_TICKS, takenThisWeek(seller, now) + knuts));
        setDirty();
    }

    /**
     * How much more an unlicensed seller may take this week; {@link Long#MAX_VALUE} when the allowance is off
     * ({@code allowance <= 0}).
     */
    public static long left(long allowance, long taken) {
        return allowance <= 0L ? Long.MAX_VALUE : Math.max(0L, allowance - taken);
    }

    /** Whether {@code seller} trades under the allowance at all: the Ministry is watching and they hold no licence. */
    public static boolean applies(ServerPlayer seller) {
        return TraceService.isActive() && Config.ministryUnlicensedSalesKnutsPerWeek > 0
                && !MinistryLicences.has(seller, LicenseType.MAGICAL_COMMERCE);
    }

    /** How much more {@code seller} may take this week at {@code now}. */
    public static long leftFor(ServerPlayer seller, long now) {
        if (!applies(seller)) {
            return Long.MAX_VALUE;
        }
        return left(Config.ministryUnlicensedSalesKnutsPerWeek,
                get(seller.level().getServer()).takenThisWeek(seller.getUUID(), now));
    }
}
