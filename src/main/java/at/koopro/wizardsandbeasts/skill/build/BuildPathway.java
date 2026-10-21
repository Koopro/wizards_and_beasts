package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.module.Module;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

/**
 * The route a build takes through the existing web, authored on a vocation as {@code "pathway"}.
 *
 * <p>Every list names nodes that already exist; a pathway re-reads the web, it does not add to it. The tiers are
 * what lets the character sheet say "early / mid / advanced" about a player's allocation, and {@link #capstoneDepth}
 * is the only rule a tier carries: how many {@link #advanced} nodes a player must hold before the vocation's
 * capstone ({@code VocationDefinition#capstoneNodeId}) may be taken.
 *
 * <p>Nodes may appear in more than one archetype's pathway — Protego is a Duelist's and a Healer's — but a capstone
 * belongs to exactly one, which {@code ArchetypeDataTest} enforces.
 *
 * @param early         what a player takes in their first sessions; the build's identity
 * @param mid           specialisation: the nodes that make two players of the same build play differently
 * @param advanced      the deep nodes; {@link #capstoneDepth} of them open the capstone
 * @param capstoneDepth advanced nodes required before the capstone is allocatable
 * @param modules       modules the build's mechanics live in; switched off, the capstone is sealed and the
 *                      sheet stops offering the build
 */
@NullMarked
public record BuildPathway(
        List<String> early,
        List<String> mid,
        List<String> advanced,
        int capstoneDepth,
        List<Module> modules) {

    public static final Codec<Module> MODULE_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(Module.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown module: " + name);
                }
            },
            module -> module.name().toLowerCase(Locale.ROOT));

    public static final Codec<BuildPathway> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("early").forGetter(BuildPathway::early),
            Codec.STRING.listOf().fieldOf("mid").forGetter(BuildPathway::mid),
            Codec.STRING.listOf().fieldOf("advanced").forGetter(BuildPathway::advanced),
            Codec.intRange(0, 16).optionalFieldOf("capstoneDepth", 1).forGetter(BuildPathway::capstoneDepth),
            MODULE_CODEC.listOf().optionalFieldOf("modules", List.of()).forGetter(BuildPathway::modules)
    ).apply(instance, BuildPathway::new));

    public BuildPathway {
        early = List.copyOf(early);
        mid = List.copyOf(mid);
        advanced = List.copyOf(advanced);
        modules = List.copyOf(modules);
    }

    /** The node list for a non-capstone tier. */
    public List<String> nodes(BuildTier tier) {
        return switch (tier) {
            case EARLY -> early;
            case MID -> mid;
            case ADVANCED -> advanced;
            case CAPSTONE -> List.of();
        };
    }
}
