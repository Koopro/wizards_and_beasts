package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Chamber of Secrets keeps one basilisk.
 *
 * <p>The structure's {@code spawn_overrides} name the basilisk as the Chamber's creature, and vanilla's natural
 * spawner offers it a place inside the Chamber's pieces on every creature cycle. Unchecked, that is a second
 * basilisk every twenty seconds and a new one each time the last is killed. {@link #mayWake} is the check — it is the
 * basilisk's spawn placement rule ({@code BeastSpawnHandler}):
 * <ul>
 *   <li>only while the {@link Module#CHAMBER_OF_SECRETS} module is on;</li>
 *   <li>only inside a Chamber, and only one whose basilisk has not been slain ({@link Data}, saved per level) —
 *       Harry killed Slytherin's monster once, and it stayed dead;</li>
 *   <li>never with another basilisk within {@link #ONE_PER} blocks.</li>
 * </ul>
 * A basilisk bred by the old ritual (a chicken's egg under a toad) is its own origin and does not go through here.
 */
public final class ChamberBasilisk {

    public static final ResourceKey<Structure> CHAMBER = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "chamber_of_secrets"));
    public static final double ONE_PER = 128.0;

    private ChamberBasilisk() {}

    /** Whether a basilisk may appear here now. */
    public static boolean mayWake(ServerLevel level, BlockPos pos, Predicate<LivingEntity> isBasilisk) {
        if (!ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS)) {
            return false;
        }
        StructureStart chamber = chamberAt(level, pos);
        if (chamber == null || Data.get(level).isSlain(chamber)) {
            return false;
        }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(ONE_PER),
                e -> e.isAlive() && isBasilisk.test(e));
        return near.isEmpty();
    }

    /** The Chamber whose pieces hold {@code pos}, or {@code null}. */
    public static StructureStart chamberAt(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(CHAMBER));
        return start.isValid() ? start : null;
    }

    /** Records that this Chamber's basilisk is dead, if it died in one. */
    public static void slainAt(ServerLevel level, BlockPos pos) {
        StructureStart chamber = chamberAt(level, pos);
        if (chamber != null) {
            Data.get(level).markSlain(chamber);
        }
    }

    /** The Chambers whose basilisk is dead, by the chunk each Chamber started in. */
    public static final class Data extends SavedData {

        public static final SavedDataType<Data> TYPE = new SavedDataType<>(
                WizardsAndBeastsMod.MODID + "_chamber_basilisks",
                Data::new,
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.LONG.listOf().optionalFieldOf("slain", List.of()).forGetter(d -> List.copyOf(d.slain))
                ).apply(instance, Data::new)));

        private final Set<Long> slain = new HashSet<>();

        public Data() {}

        private Data(List<Long> slain) {
            this.slain.addAll(slain);
        }

        public static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(TYPE);
        }

        public boolean isSlain(StructureStart chamber) {
            return slain.contains(chamber.getChunkPos().toLong());
        }

        public void markSlain(StructureStart chamber) {
            if (slain.add(chamber.getChunkPos().toLong())) {
                setDirty();
            }
        }
    }

    /** Placement predicate shape for {@code BeastSpawnHandler}: a basilisk is any mob of the given type. */
    public static Predicate<LivingEntity> ofType(net.minecraft.world.entity.EntityType<? extends PathfinderMob> type) {
        return e -> e.getType() == type;
    }
}
