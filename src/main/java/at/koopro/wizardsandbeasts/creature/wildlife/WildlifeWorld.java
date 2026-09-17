package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the world facts {@link WildlifeRules} decides on: what a player knows about a species, whether they have
 * killed one, how dark their soul is. The one place those lookups live, so the unicorn's goal, its grooming and the
 * game tests all ask the same question.
 */
@NullMarked
public final class WildlifeWorld {

    private WildlifeWorld() {}

    /** The deepest tier this player holds on any page describing {@code species}. */
    public static DiscoveryTier tierFor(Player player, EntityType<?> species) {
        DiscoveryTier best = DiscoveryTier.UNKNOWN;
        for (BestiaryEntry entry : BestiaryDiscoveryHandler.entriesFor(species)) {
            DiscoveryTier tier = BestiaryDataHelper.getTier(player, entry.id());
            if (tier.ordinal() > best.ordinal()) {
                best = tier;
            }
        }
        return best;
    }

    /** Whether a wary creature of {@code species} lets this player near right now. */
    public static boolean letsNear(Player player, EntityType<?> species, boolean puritySensitive, String slayerFlag) {
        return WildlifeRules.letsNear(tierFor(player, species), player.isShiftKeyDown(),
                player.getMainHandItem().isEmpty(), puritySensitive, isSlayer(player, slayerFlag),
                DarkCorruptionService.get(player));
    }

    /** Whether a purity-sensitive creature flees this player from further off. */
    public static boolean shuns(Player player, String slayerFlag) {
        return WildlifeRules.shuns(isSlayer(player, slayerFlag), DarkCorruptionService.get(player));
    }

    public static boolean isSlayer(Player player, String slayerFlag) {
        return !slayerFlag.isBlank() && PlayerAbilityHelper.hasAbilityFlag(player, slayerFlag);
    }
}
