package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Which creatures the mod claims are finished.
 *
 * <p>{@code Module.CREATURES} registers 111 entity types. Twelve of them are built; the rest are
 * data-complete but wear a bulk-generated box rig — six to nine cubes, a body, a head and four
 * sticks. Shipping all 111 as if they were equal is the difference between an alpha and a promise,
 * so this class is the single place that names the difference, and every surface that has to treat
 * the two groups differently reads it here: natural spawning, the creative-tab spawn eggs, the
 * {@code /wandb beast creature list} report and {@code documentation/KNOWN_ISSUES.md}.
 *
 * <h2>What "alpha" means here</h2>
 * A creature is on this list when all four hold:
 * <ol>
 *   <li>a hand-built rig, not a generated box rig (the practical threshold is ~17 cubes; the box
 *       rigs top out at 9 and the built ones start at 17, so nothing sits near the line);</li>
 *   <li>working AI beyond wander-and-look — either a bespoke entity class or a
 *       {@code CreatureDefinition} carrying real {@code abilities};</li>
 *   <li>a loot table, so killing it is not a no-op;</li>
 *   <li>a bestiary entry that names its {@code entityType}, so encountering it fills a page.</li>
 * </ol>
 *
 * <p>Adding an id here is therefore a claim about four separate things being true, not a label, and
 * {@code AlphaRosterAssetTest} checks all four against the files on disk rather than trusting it.
 *
 * <h2>Why the eight extra dragon breeds joined</h2>
 * They failed exactly one of the four tests and it was the cheapest one. {@code antipodean_opaleye},
 * {@code chinese_fireball}, {@code hebridean_black}, {@code norwegian_ridgeback},
 * {@code peruvian_vipertooth}, {@code romanian_longhorn}, {@code swedish_short_snout} and
 * {@code ukrainian_ironbelly} carry hand-built 23–32 cube rigs, run {@code DragonEntity}'s
 * fire/venom kit, and have bestiary entries naming their {@code entityType} — they simply had no
 * loot table, so killing one was a no-op. They have one now.
 *
 * <p>The earlier reason for holding them back was that "an alpha slice of twenty is not a slice".
 * That argument is about the count, and the count is misleading here: these eight are one family
 * with one shared implementation, not eight unrelated creatures. Ten dragon breeds and ten
 * everything-else is a slice a player can tell the shape of.
 */
@NullMarked
public final class AlphaRoster {

    /**
     * The alpha slice. Ordered by how a player is likely to meet them: the companions and the quiet
     * ones first, the things that fight back last.
     */
    public static final Set<String> SHIPPED = Set.of(
            // -- bespoke entity classes --
            "niffler",            // theft + pouch + bonding; the mod's most complete creature
            "bowtruckle",         // tempted by sticks, panics; tree-guardian
            "mooncalf",           // dances under a full moon
            "thestral",           // grazes, rideable, visible only to those who have seen death
            "phoenix",            // fire-immune flyer
            // -- generic entity + CreatureDefinition abilities --
            "ghoul",              // attic haunt
            "hippogriff",         // enrage + dive bomb; rideable aerial
            "obscurus",           // tinted smoke-form aerial threat
            "werewolf",           // moon-driven hostile
            "basilisk",           // lethal gaze; boss threat
            "occamy",             // choranaptyxic: it resizes to fit the space, hitbox and all
            // -- the ten dragon breeds; one family, one shared kit, one standard --
            "common_welsh_green", // starter dragon, and the wild source of dragon heartstring
            "hungarian_horntail", // apex dragon
            "antipodean_opaleye",
            "chinese_fireball",
            "hebridean_black",
            "norwegian_ridgeback",
            "peruvian_vipertooth",
            "romanian_longhorn",
            "swedish_short_snout",
            "ukrainian_ironbelly"
    );

    /**
     * Creatures that keep spawning naturally even when the roster is restricted to the alpha slice,
     * because the mod's own progression dead-ends without them.
     *
     * <p>The Unicorn is the wild source of unicorn hair, one of the three wand cores. Restricting
     * spawns to finished creatures must not make the Wandmaker's Bench unusable, so the gate asks
     * "alpha or load-bearing", not "alpha". The Unicorn still wears a placeholder rig and is still
     * listed as such in {@code KNOWN_ISSUES.md} — this exempts it from the spawn gate, not from the
     * honesty.
     */
    public static final Set<String> PROGRESSION_CRITICAL = Set.of("unicorn");

    private AlphaRoster() {}

    /** True when {@code id} is a bare creature id (no namespace) on the alpha roster. */
    public static boolean isAlpha(String id) {
        return SHIPPED.contains(id);
    }

    /** True for this mod's creature ids on the alpha roster; false for anything from another mod. */
    public static boolean isAlpha(@Nullable Identifier id) {
        return id != null && WizardsAndBeastsMod.MODID.equals(id.getNamespace()) && isAlpha(id.getPath());
    }

    public static boolean isAlpha(EntityType<?> type) {
        return isAlpha(type.builtInRegistryHolder().key().identifier());
    }

    /** True when this creature may spawn naturally under the {@code ALPHA_ONLY} spawn setting. */
    public static boolean mayNaturallySpawnInAlphaSlice(EntityType<?> type) {
        Identifier id = type.builtInRegistryHolder().key().identifier();
        if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace())) {
            return true;
        }
        return isAlpha(id.getPath()) || PROGRESSION_CRITICAL.contains(id.getPath());
    }
}
