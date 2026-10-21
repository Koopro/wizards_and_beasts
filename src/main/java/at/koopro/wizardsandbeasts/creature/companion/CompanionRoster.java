package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An owner's companions as last seen, kept on the player.
 *
 * <p>The creatures themselves live in chunks that may be unloaded, in another dimension or a pocket trunk;
 * the Beasts tab still has to show them. Each companion writes its entry here when it changes (see
 * {@link Companions#refreshRoster}), the owner's client gets the roster on change, and the entry outlives the
 * creature as a memorial. It is also where the owner's trusted and hostile players are kept: the source of
 * truth that each companion copies when it next sees its owner.
 *
 * @param entries companion entity id → its last known state, in the order they were met
 * @param trusted players the owner trusts with their creatures
 * @param hostile players the owner's creatures treat as enemies
 * @param names   player id → name, for the lists above (a name is not otherwise known offline)
 */
@NullMarked
public record CompanionRoster(Map<UUID, Entry> entries, Set<UUID> trusted, Set<UUID> hostile, Map<UUID, String> names) {

    /** Entries kept: living companions always; the oldest memorials make room past this. */
    public static final int MAX_ENTRIES = 32;

    private static final Codec<Set<UUID>> UUID_SET = UUIDUtil.STRING_CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    public static final Codec<CompanionRoster> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC).optionalFieldOf("entries", Map.of())
                    .forGetter(CompanionRoster::entries),
            UUID_SET.optionalFieldOf("trusted", Set.of()).forGetter(CompanionRoster::trusted),
            UUID_SET.optionalFieldOf("hostile", Set.of()).forGetter(CompanionRoster::hostile),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("names", Map.of())
                    .forGetter(CompanionRoster::names)
    ).apply(i, CompanionRoster::new));

    public static final CompanionRoster EMPTY = new CompanionRoster(Map.of(), Set.of(), Set.of(), Map.of());

    public CompanionRoster {
        entries = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        trusted = Set.copyOf(trusted);
        hostile = Set.copyOf(hostile);
        names = Map.copyOf(names);
    }

    /**
     * One companion as last seen.
     *
     * @param commands commands the species understands and this creature could ever do
     * @param usable   of those, the ones it takes right now
     * @param abilities ability lang keys
     */
    public record Entry(Identifier species, String name, int bond, int maxBond, CompanionStage stage,
                        List<String> traits, int training, int trainedAt, int masteredAt, float health,
                        float maxHealth, CompanionCommand mode, String dimension, int x, int y, int z, boolean alive,
                        long lastSeenDay, List<CompanionMemory.HistoryEntry> history, int generation,
                        List<String> parents, List<CompanionCommand> commands, List<CompanionCommand> usable,
                        List<String> abilities) {

        private static final Codec<CompanionStage> STAGE = Codec.STRING.xmap(s -> {
            try {
                return CompanionStage.valueOf(s);
            } catch (IllegalArgumentException e) {
                return CompanionStage.WILD;
            }
        }, CompanionStage::name);

        /** Two halves: the codec builder takes sixteen fields at most. */
        private record Body(Identifier species, String name, int bond, int maxBond, CompanionStage stage,
                            List<String> traits, int training, int trainedAt, int masteredAt, float health,
                            float maxHealth, CompanionCommand mode) {}

        private record Place(String dimension, int x, int y, int z, boolean alive, long lastSeenDay,
                             List<CompanionMemory.HistoryEntry> history, int generation, List<String> parents,
                             List<CompanionCommand> commands, List<CompanionCommand> usable, List<String> abilities) {}

        private static final Codec<Body> BODY = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("species").forGetter(Body::species),
                Codec.STRING.fieldOf("name").forGetter(Body::name),
                Codec.INT.fieldOf("bond").forGetter(Body::bond),
                Codec.INT.fieldOf("max_bond").forGetter(Body::maxBond),
                STAGE.fieldOf("stage").forGetter(Body::stage),
                Codec.STRING.listOf().fieldOf("traits").forGetter(Body::traits),
                Codec.INT.fieldOf("training").forGetter(Body::training),
                Codec.INT.fieldOf("trained_at").forGetter(Body::trainedAt),
                Codec.INT.fieldOf("mastered_at").forGetter(Body::masteredAt),
                Codec.FLOAT.fieldOf("health").forGetter(Body::health),
                Codec.FLOAT.fieldOf("max_health").forGetter(Body::maxHealth),
                CompanionCommand.CODEC.fieldOf("mode").forGetter(Body::mode)
        ).apply(i, Body::new));

        private static final Codec<Place> PLACE = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Place::dimension),
                Codec.INT.fieldOf("x").forGetter(Place::x),
                Codec.INT.fieldOf("y").forGetter(Place::y),
                Codec.INT.fieldOf("z").forGetter(Place::z),
                Codec.BOOL.fieldOf("alive").forGetter(Place::alive),
                Codec.LONG.fieldOf("last_seen_day").forGetter(Place::lastSeenDay),
                CompanionMemory.HistoryEntry.CODEC.listOf().fieldOf("history").forGetter(Place::history),
                Codec.INT.fieldOf("generation").forGetter(Place::generation),
                Codec.STRING.listOf().fieldOf("parents").forGetter(Place::parents),
                CompanionCommand.CODEC.listOf().fieldOf("commands").forGetter(Place::commands),
                CompanionCommand.CODEC.listOf().fieldOf("usable").forGetter(Place::usable),
                Codec.STRING.listOf().fieldOf("abilities").forGetter(Place::abilities)
        ).apply(i, Place::new));

        public static final Codec<Entry> CODEC = Codec.pair(BODY.fieldOf("body").codec(), PLACE.fieldOf("place").codec())
                .xmap(p -> new Entry(p.getFirst().species(), p.getFirst().name(), p.getFirst().bond(),
                                p.getFirst().maxBond(), p.getFirst().stage(), p.getFirst().traits(), p.getFirst().training(),
                                p.getFirst().trainedAt(), p.getFirst().masteredAt(), p.getFirst().health(),
                                p.getFirst().maxHealth(), p.getFirst().mode(), p.getSecond().dimension(), p.getSecond().x(),
                                p.getSecond().y(), p.getSecond().z(), p.getSecond().alive(), p.getSecond().lastSeenDay(),
                                p.getSecond().history(), p.getSecond().generation(), p.getSecond().parents(),
                                p.getSecond().commands(), p.getSecond().usable(), p.getSecond().abilities()),
                        e -> com.mojang.datafixers.util.Pair.of(
                                new Body(e.species(), e.name(), e.bond(), e.maxBond(), e.stage(), e.traits(), e.training(),
                                        e.trainedAt(), e.masteredAt(), e.health(), e.maxHealth(), e.mode()),
                                new Place(e.dimension(), e.x(), e.y(), e.z(), e.alive(), e.lastSeenDay(), e.history(),
                                        e.generation(), e.parents(), e.commands(), e.usable(), e.abilities())));

        public Entry {
            traits = List.copyOf(traits);
            history = List.copyOf(history);
            parents = List.copyOf(parents);
            commands = List.copyOf(commands);
            usable = List.copyOf(usable);
            abilities = List.copyOf(abilities);
        }

        public Entry deceased(long day, CompanionMemory.HistoryEntry note) {
            List<CompanionMemory.HistoryEntry> next = new ArrayList<>(history);
            next.add(note);
            while (next.size() > CompanionMemory.MAX_HISTORY) {
                next.removeFirst();
            }
            return new Entry(species, name, bond, maxBond, stage, traits, training, trainedAt, masteredAt, 0f, maxHealth,
                    mode, dimension, x, y, z, false, day, next, generation, parents, List.of(), List.of(), abilities);
        }
    }

    public CompanionRoster with(UUID id, Entry entry) {
        LinkedHashMap<UUID, Entry> next = new LinkedHashMap<>(entries);
        next.put(id, entry);
        // Over the limit, the oldest memorial goes first; living companions are never dropped.
        while (next.size() > MAX_ENTRIES) {
            UUID oldestDead = null;
            for (Map.Entry<UUID, Entry> e : next.entrySet()) {
                if (!e.getValue().alive()) {
                    oldestDead = e.getKey();
                    break;
                }
            }
            if (oldestDead == null) {
                break;
            }
            next.remove(oldestDead);
        }
        return new CompanionRoster(next, trusted, hostile, names);
    }

    public CompanionRoster without(UUID id) {
        LinkedHashMap<UUID, Entry> next = new LinkedHashMap<>(entries);
        next.remove(id);
        return new CompanionRoster(next, trusted, hostile, names);
    }

    public CompanionRoster withRelation(UUID player, String name, boolean trust, boolean hostileNow) {
        Set<UUID> nextTrusted = new HashSet<>(trusted);
        Set<UUID> nextHostile = new HashSet<>(hostile);
        nextTrusted.remove(player);
        nextHostile.remove(player);
        if (trust) {
            nextTrusted.add(player);
        }
        if (hostileNow) {
            nextHostile.add(player);
        }
        Map<UUID, String> nextNames = new HashMap<>(names);
        if (trust || hostileNow) {
            nextNames.put(player, name);
        } else {
            nextNames.remove(player);
        }
        return new CompanionRoster(entries, nextTrusted, nextHostile, nextNames);
    }

    /** Network form: the same codec over NBT, for the owner's client. */
    public static final StreamCodec<io.netty.buffer.ByteBuf, CompanionRoster> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);
}
