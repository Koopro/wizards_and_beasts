package at.koopro.wizardsandbeasts.wand.rules;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Where a world keeps the woods, cores and pairings withdrawn from wandmaking. */
@NullMarked
public final class WandRulesData extends SavedData {

    public static final SavedDataType<WandRulesData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_wand_rules",
            WandRulesData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.listOf().optionalFieldOf("disabledWoods", List.of()).forGetter(d -> List.copyOf(d.woods)),
                    Codec.STRING.listOf().optionalFieldOf("disabledCores", List.of()).forGetter(d -> List.copyOf(d.cores)),
                    Codec.STRING.listOf().optionalFieldOf("disabledPairs", List.of()).forGetter(d -> List.copyOf(d.pairs))
            ).apply(instance, WandRulesData::new)));

    private final Set<String> woods = new HashSet<>();
    private final Set<String> cores = new HashSet<>();
    private final Set<String> pairs = new HashSet<>();

    private WandRulesData() {
    }

    private WandRulesData(List<String> woods, List<String> cores, List<String> pairs) {
        this.woods.addAll(woods);
        this.cores.addAll(cores);
        this.pairs.addAll(pairs);
    }

    public static WandRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Set<String> woods() {
        return Set.copyOf(woods);
    }

    public Set<String> cores() {
        return Set.copyOf(cores);
    }

    public Set<String> pairs() {
        return Set.copyOf(pairs);
    }

    public void setWood(String key, boolean disabled) {
        if (disabled ? woods.add(key) : woods.remove(key)) {
            setDirty();
        }
    }

    public void setCore(String key, boolean disabled) {
        if (disabled ? cores.add(key) : cores.remove(key)) {
            setDirty();
        }
    }

    public void setPair(String key, boolean disabled) {
        if (disabled ? pairs.add(key) : pairs.remove(key)) {
            setDirty();
        }
    }
}
