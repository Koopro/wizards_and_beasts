package at.koopro.wizardsandbeasts.skill.build;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A recognisable build: a vocation that carries a {@link BuildPathway} and a capstone.
 *
 * <p>Pure view over {@code VocationDefinition} so the rules in {@link BuildRules} and {@link BuildProfile} run in
 * a unit test without a datapack or a player.
 *
 * @param id        the vocation id, e.g. {@code wizards_and_beasts:duelist}
 * @param nameKey   the vocation's display-name translation key
 * @param pathway   early / mid / advanced node lists and the capstone rule
 * @param capstone  the capstone node id (a plain skill id)
 */
@NullMarked
public record Archetype(Identifier id, String nameKey, BuildPathway pathway, String capstone) {

    /** The tier {@code nodeId} occupies on this pathway, or empty when the node is not part of the build. */
    public Optional<BuildTier> tierOf(String nodeId) {
        if (capstone.equals(nodeId)) {
            return Optional.of(BuildTier.CAPSTONE);
        }
        for (BuildTier tier : new BuildTier[]{BuildTier.EARLY, BuildTier.MID, BuildTier.ADVANCED}) {
            if (pathway.nodes(tier).contains(nodeId)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public boolean contains(@Nullable String nodeId) {
        return nodeId != null && tierOf(nodeId).isPresent();
    }
}
