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
                            .forGetter(HousePoints::stored)
            ).apply(i, HousePoints::new)));

    private final Map<House, Integer> points = new EnumMap<>(House.class);

    public HousePoints() {}

    private HousePoints(Map<String, Integer> stored) {
        stored.forEach((id, n) -> {
            House house = House.byId(id);
            if (house != null) {
                points.put(house, n);
            }
        });
    }

    private Map<String, Integer> stored() {
        Map<String, Integer> out = new HashMap<>();
        points.forEach((h, n) -> out.put(h.id(), n));
        return out;
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
