package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** The House Cup: each house's points this server, kept with the world. */
@NullMarked
public final class HousePoints extends SavedData {

    public static final SavedDataType<HousePoints> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_house_points",
            HousePoints::new,
            RecordCodecBuilder.create(i -> i.group(
                    Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("points", Map.of())
                            .forGetter(HousePoints::stored),
                    Codec.LONG.optionalFieldOf("settled_cycle", -1L).forGetter(h -> h.settledCycle),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("cups", Map.of())
                            .forGetter(h -> byId(h.cups))
            ).apply(i, HousePoints::new)));

    private final Map<House, Integer> points = new EnumMap<>(House.class);
    /** House Cups won, all time. */
    private final Map<House, Integer> cups = new EnumMap<>(House.class);
    /** The House Cup cycle last settled ({@link HouseCup}); -1 before the first one is seen. */
    private long settledCycle = -1L;

    public HousePoints() {}

    private HousePoints(Map<String, Integer> stored, long settledCycle, Map<String, Integer> cups) {
        read(stored, points);
        read(cups, this.cups);
        this.settledCycle = settledCycle;
    }

    private static void read(Map<String, Integer> from, Map<House, Integer> into) {
        from.forEach((id, n) -> {
            House house = House.byId(id);
            if (house != null) {
                into.put(house, n);
            }
        });
    }

    private static Map<String, Integer> byId(Map<House, Integer> from) {
        Map<String, Integer> out = new HashMap<>();
        from.forEach((h, n) -> out.put(h.id(), n));
        return out;
    }

    private Map<String, Integer> stored() {
        return byId(points);
    }

    public long settledCycle() {
        return settledCycle;
    }

    public int cupsWon(House house) {
        return cups.getOrDefault(house, 0);
    }

    /** Closes a House Cup cycle: the winner (if any) gets the Cup, every house starts again from nothing. */
    void settle(long cycle, @org.jspecify.annotations.Nullable House winner) {
        if (winner != null) {
            cups.merge(winner, 1, Integer::sum);
        }
        points.clear();
        settledCycle = cycle;
        setDirty();
    }

    /** Remembers the cycle without awarding anything: the first cycle a world sees, or a test reset. */
    void markCycle(long cycle) {
        settledCycle = cycle;
        setDirty();
    }

    public static HousePoints get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int of(House house) {
        return points.getOrDefault(house, 0);
    }

    public void add(House house, int delta) {
        points.merge(house, delta, Integer::sum);
        setDirty();
    }

    public boolean any() {
        return points.values().stream().anyMatch(n -> n != 0);
    }
}
