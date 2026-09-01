package at.koopro.wizardsandbeasts.standing.gate;

import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A datapack rule that closes part of the skill web to a player whose standing is wrong for it.
 *
 * <p>Lives at {@code data/<namespace>/standing_gates/<id>.json}:
 *
 * <pre>{@code
 * {
 *   "tree": "dark_arts",
 *   "requirements": [ { "axis": "alignment", "maxBand": "neutral" } ]
 * }
 * }</pre>
 *
 * <p><b>The mod ships none of these.</b> The mechanism is live, loaded and tested, but authoring a gate
 * changes what existing players can reach in a save they have already spent points in, and that is a
 * content decision rather than an infrastructure one. A pack — including this mod's own, later — adds a
 * file and the gate applies with no code change.
 *
 * <p>Kept out of {@code Skill}'s own codec deliberately. Putting the requirement on the node would mean
 * editing 168 shipped JSON files to gate two of them, and would make a server operator fork the mod's
 * data to change their mind. A separate file that names its target keeps content and policy apart.
 *
 * @param tree         gate the whole web; exactly one of this and {@code node} is set
 * @param node         gate a single node by id
 * @param requirements every one must hold — the list is an AND. An OR is expressible as two gates only
 *                     if they name different targets, which is deliberate: "either of these two very
 *                     different lives" is a design smell in a progression gate.
 */
@NullMarked
public record StandingGate(@Nullable SkillTreeId tree,
                           @Nullable String node,
                           List<StandingRequirement> requirements) {

    public static final Codec<StandingGate> CODEC = RecordCodecBuilder.<StandingGate>create(
            instance -> instance.group(
                    SkillTreeId.CODEC.optionalFieldOf("tree").forGetter(g -> Optional.ofNullable(g.tree())),
                    Codec.STRING.optionalFieldOf("node").forGetter(g -> Optional.ofNullable(g.node())),
                    StandingRequirement.CODEC.listOf().fieldOf("requirements")
                            .forGetter(StandingGate::requirements)
            ).apply(instance, (tree, node, requirements) ->
                    new StandingGate(tree.orElse(null), node.orElse(null), requirements)))
            .validate(StandingGate::validate);

    public StandingGate {
        requirements = List.copyOf(requirements);
    }

    private static DataResult<StandingGate> validate(StandingGate gate) {
        boolean hasTree = gate.tree != null;
        boolean hasNode = gate.node != null && !gate.node.isBlank();
        if (hasTree == hasNode) {
            return DataResult.error(() -> "a standing gate must name exactly one of 'tree' or 'node'; "
                    + (hasTree ? "both were given" : "neither was given"));
        }
        if (gate.requirements.isEmpty()) {
            return DataResult.error(() -> "a standing gate with no requirements gates nothing");
        }
        return DataResult.success(gate);
    }

    /**
     * The first requirement this player fails, or {@code null} if the gate is open to them. Returning
     * the failing requirement rather than a boolean is what lets the refusal toast say which axis is
     * in the way instead of "locked".
     */
    public @Nullable StandingRequirement firstUnmet(StandingLookup lookup) {
        for (StandingRequirement requirement : requirements) {
            if (!requirement.isSatisfiedBy(lookup.bandOf(requirement.axis()))) {
                return requirement;
            }
        }
        return null;
    }

    /**
     * How a gate reads a player's bands.
     *
     * <p>An interface rather than a {@code Player} parameter so gate evaluation stays testable without
     * a server: the production implementation is one method reference to {@code StandingService}, and a
     * test supplies bands directly.
     */
    @FunctionalInterface
    public interface StandingLookup {
        at.koopro.wizardsandbeasts.standing.StandingBand bandOf(
                at.koopro.wizardsandbeasts.standing.StandingAxis axis);
    }
}
