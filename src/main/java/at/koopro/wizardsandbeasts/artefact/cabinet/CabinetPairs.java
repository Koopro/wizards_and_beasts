package at.koopro.wizardsandbeasts.artefact.cabinet;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every linked pair of Vanishing Cabinets in the world -- the one place a link is recorded.
 *
 * <p>Kept in the overworld's storage (as the Floo Network is), so it is loaded whenever the server is, whichever
 * dimension either cabinet stands in and whichever chunk loads first. A cabinet block holds only who placed it;
 * whether it is linked, and to where, is asked of this. Two copies of a link -- one in each block entity and one
 * here -- would be two answers the moment one end is broken while the other is unloaded.
 *
 * <p>The reverse index (position to pair) is rebuilt on load rather than saved.
 */
@NullMarked
public final class CabinetPairs extends SavedData {

    /** One linked pair: both cabinets' lower halves, who linked them, and whether anyone may step through. */
    public record Pair(UUID id, GlobalPos a, GlobalPos b, UUID owner, boolean shared) {
        public static final Codec<Pair> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Pair::id),
                GlobalPos.CODEC.fieldOf("a").forGetter(Pair::a),
                GlobalPos.CODEC.fieldOf("b").forGetter(Pair::b),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Pair::owner),
                Codec.BOOL.optionalFieldOf("shared", false).forGetter(Pair::shared)
        ).apply(i, Pair::new));

        /** The other end, seen from {@code from}; null when {@code from} is neither end. */
        public @Nullable GlobalPos partnerOf(GlobalPos from) {
            if (from.equals(a)) {
                return b;
            }
            return from.equals(b) ? a : null;
        }

        Pair withShared(boolean value) {
            return new Pair(id, a, b, owner, value);
        }
    }

    public static final Codec<CabinetPairs> CODEC = Pair.CODEC.listOf()
            .optionalFieldOf("pairs", List.of())
            .codec()
            .xmap(CabinetPairs::new, CabinetPairs::list);

    public static final SavedDataType<CabinetPairs> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_vanishing_cabinets", CabinetPairs::new, CODEC);

    private final Map<UUID, Pair> pairs = new LinkedHashMap<>();
    private final Map<GlobalPos, UUID> byPos = new HashMap<>();

    public CabinetPairs() {}

    private CabinetPairs(List<Pair> loaded) {
        for (Pair pair : loaded) {
            // A position recorded twice (an older save, a hand edit) keeps its first pair; the second is dropped
            // rather than leaving one cabinet answering for two links.
            if (byPos.containsKey(pair.a()) || byPos.containsKey(pair.b()) || pair.a().equals(pair.b())) {
                continue;
            }
            put(pair);
        }
    }

    public static CabinetPairs get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private List<Pair> list() {
        return List.copyOf(pairs.values());
    }

    private void put(Pair pair) {
        pairs.put(pair.id(), pair);
        byPos.put(pair.a(), pair.id());
        byPos.put(pair.b(), pair.id());
    }

    /** The pair a cabinet at {@code pos} belongs to, or null when it stands alone. */
    public @Nullable Pair pairAt(GlobalPos pos) {
        UUID id = byPos.get(pos);
        return id == null ? null : pairs.get(id);
    }

    /**
     * Links two cabinets. Refused (null) when either already belongs to a pair or they are the same cabinet --
     * linking never silently breaks an existing pair.
     */
    public @Nullable Pair link(GlobalPos a, GlobalPos b, UUID owner) {
        if (a.equals(b) || byPos.containsKey(a) || byPos.containsKey(b)) {
            return null;
        }
        Pair pair = new Pair(UUID.randomUUID(), a, b, owner, false);
        put(pair);
        setDirty();
        return pair;
    }

    /** Dissolves the pair a cabinet at {@code pos} belongs to. Returns the dissolved pair, or null if there was none. */
    public @Nullable Pair sever(GlobalPos pos) {
        UUID id = byPos.get(pos);
        if (id == null) {
            return null;
        }
        Pair pair = pairs.remove(id);
        if (pair != null) {
            byPos.remove(pair.a());
            byPos.remove(pair.b());
            setDirty();
        }
        return pair;
    }

    /** Sets whether anyone may use the pair at {@code pos}; returns the updated pair, or null when unlinked. */
    public @Nullable Pair setShared(GlobalPos pos, boolean shared) {
        Pair pair = pairAt(pos);
        if (pair == null) {
            return null;
        }
        Pair updated = pair.withShared(shared);
        pairs.put(updated.id(), updated);
        setDirty();
        return updated;
    }

    public int size() {
        return pairs.size();
    }

    List<Pair> snapshot() {
        return new ArrayList<>(pairs.values());
    }
}
