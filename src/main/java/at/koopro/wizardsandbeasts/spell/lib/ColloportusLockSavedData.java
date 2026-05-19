package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Per-dimension persisted seals for {@code Colloportus}. Stored under the level's {@code data/} folder.
 */
public final class ColloportusLockSavedData extends SavedData {

    public static final SavedDataType<ColloportusLockSavedData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_colloportus_locks",
            ColloportusLockSavedData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.listOf().fieldOf("positions").forGetter(ColloportusLockSavedData::positionsAsList)
            ).apply(instance, ColloportusLockSavedData::fromList)));

    private final LongOpenHashSet locked;

    public ColloportusLockSavedData() {
        this(new LongOpenHashSet());
    }

    private ColloportusLockSavedData(LongOpenHashSet locked) {
        this.locked = locked;
    }

    private static ColloportusLockSavedData fromList(List<Long> positions) {
        LongOpenHashSet set = new LongOpenHashSet();
        if (positions != null) {
            for (Long l : positions) {
                if (l != null) {
                    set.add(l.longValue());
                }
            }
        }
        return new ColloportusLockSavedData(set);
    }

    private List<Long> positionsAsList() {
        long[] arr = locked.toLongArray();
        return Arrays.stream(arr).boxed().toList();
    }

    public boolean isLocked(BlockPos pos) {
        return locked.contains(pos.asLong());
    }

    public void lock(BlockPos pos) {
        if (locked.add(pos.asLong())) {
            setDirty();
        }
    }

    /** @return true if a seal existed at this position */
    public boolean remove(BlockPos pos) {
        if (locked.remove(pos.asLong())) {
            setDirty();
            return true;
        }
        return false;
    }
}
