package at.koopro.wizardsandbeasts.event.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks disarm timestamps per wand instance id for allegiance transfer (per disarmer player).
 */
public record DisarmLogState(Map<UUID, List<Long>> entries) {

    public static final DisarmLogState EMPTY = new DisarmLogState(new HashMap<>());

    public static final Codec<DisarmLogState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.CODEC, Codec.list(Codec.LONG)).fieldOf("entries").forGetter(DisarmLogState::entries)
    ).apply(inst, m -> new DisarmLogState(new HashMap<>(m))));

    public static DisarmLogState copyOf(Map<UUID, List<Long>> map) {
        Map<UUID, List<Long>> copy = new HashMap<>();
        for (Map.Entry<UUID, List<Long>> e : map.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return new DisarmLogState(copy);
    }
}
