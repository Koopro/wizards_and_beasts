package at.koopro.wizardsandbeasts.treasure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A kind of place goblins once buried treasure in, loaded from {@code data/<ns>/treasure_sites/<id>.json}. The world
 * already builds these — desert tombs, jungle temples, sunken ruins, the ancient cities — so the mod adds no
 * structure: it decides which of their chests may hold a cursed artefact, and how badly cursed.
 *
 * <pre>{@code
 * { "structures": "wizards_and_beasts:treasure_site/desert_tomb",
 *   "loot_tables": ["minecraft:chests/desert_pyramid"],
 *   "chance": 0.12, "expedition_chance": 0.8,
 *   "value": { "min": 290, "max": 870 }, "layers": { "min": 1, "max": 2 },
 *   "curses": ["jinx", "flagrante"],
 *   "artefacts": ["wizards_and_beasts:ancient_goblet", "wizards_and_beasts:sealed_casket"] }
 * }</pre>
 *
 * @param structures       a structure tag the Gringotts chart points at
 * @param lootTables       chests whose loot may carry an artefact
 * @param chance           per chest, for anyone
 * @param expeditionChance per chest, for someone holding a Gringotts expedition to this site
 * @param value            the artefact's worth in Knuts
 * @param layers           how many layers of curse
 * @param curses           which curses are laid here
 * @param artefacts        which artefact items are found here
 */
@NullMarked
public record TreasureSite(Identifier structures, List<Identifier> lootTables, float chance, float expeditionChance,
                           Range value, Range layers, List<CurseKind> curses, List<Identifier> artefacts) {

    public static final Codec<TreasureSite> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("structures").forGetter(TreasureSite::structures),
            Identifier.CODEC.listOf(1, 16).fieldOf("loot_tables").forGetter(TreasureSite::lootTables),
            Codec.floatRange(0.0f, 1.0f).fieldOf("chance").forGetter(TreasureSite::chance),
            Codec.floatRange(0.0f, 1.0f).fieldOf("expedition_chance").forGetter(TreasureSite::expeditionChance),
            Range.CODEC.fieldOf("value").forGetter(TreasureSite::value),
            Range.CODEC.fieldOf("layers").forGetter(TreasureSite::layers),
            CurseKind.CODEC.listOf(1, 8).fieldOf("curses").forGetter(TreasureSite::curses),
            Identifier.CODEC.listOf(1, 16).fieldOf("artefacts").forGetter(TreasureSite::artefacts)
    ).apply(i, TreasureSite::new));

    public TreasureSite {
        lootTables = List.copyOf(lootTables);
        curses = List.copyOf(curses);
        artefacts = List.copyOf(artefacts);
    }

    /** An inclusive whole-number range. */
    public record Range(int min, int max) {
        public static final Codec<Range> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 1_000_000).fieldOf("min").forGetter(Range::min),
                Codec.intRange(0, 1_000_000).fieldOf("max").forGetter(Range::max)
        ).apply(i, Range::new));

        public int roll(RandomSource random) {
            return max <= min ? min : min + random.nextInt(max - min + 1);
        }
    }

    /** The artefact's identity as found: cursed, unread, valued. The item is {@link #artefacts} rolled separately. */
    public TreasureArtefact roll(Identifier siteId, RandomSource random) {
        CurseKind curse = curses.get(random.nextInt(curses.size()));
        return new TreasureArtefact(siteId, Optional.of(curse), Math.max(1, layers.roll(random)), false,
                value.roll(random));
    }

    public Identifier rollItem(RandomSource random) {
        return artefacts.get(random.nextInt(artefacts.size()));
    }

    /** Problems the codec cannot see. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (value.max() < value.min()) {
            problems.add("value max below min");
        }
        if (layers.min() < 1 || layers.max() < layers.min() || layers.max() > 16) {
            problems.add("layers must be 1..16 with max >= min");
        }
        if (expeditionChance < chance) {
            problems.add("an expedition must not find less than a passer-by");
        }
        return problems;
    }
}
