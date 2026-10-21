package at.koopro.wizardsandbeasts.creature.companion;

import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the companion layer adds to the bond layer's breeding: who can pair, where young are raised, and who the
 * young turn out to be. The mechanics of love, cooldowns and growth stay in {@code BondableBeast}; this is called
 * from the two points where it pairs and where it births.
 *
 * <p>Deliberately not a genetics model. A juvenile takes one personality trait from each parent (sometimes a
 * fresh one instead), its generation, its parents' names and its birthday — enough that a keeper can follow a
 * line and recognise a temperament in it.
 */
@NullMarked
public final class CompanionBreeding {

    private CompanionBreeding() {}

    /** Same species always; another only when the species' companion profile names it. */
    public static boolean compatible(EntityType<?> self, EntityType<?> other) {
        if (self == other) {
            return true;
        }
        CompanionProfile profile = CompanionData.profile(self);
        return profile != null && profile.breeding()
                .map(b -> b.compatible().contains(BuiltInRegistries.ENTITY_TYPE.getKey(other)))
                .orElse(false);
    }

    /** Whether the pair's surroundings suit the species; true when it declares no habitat. */
    public static boolean habitatOk(BondableBeast beast) {
        CompanionProfile profile = Companions.profile(beast);
        if (profile == null || profile.breeding().isEmpty() || profile.breeding().get().habitat().isEmpty()) {
            return true;
        }
        CompanionProfile.Habitat habitat = profile.breeding().get().habitat().get();
        PathfinderMob mob = beast.bondMob();
        Level level = mob.level();
        BlockPos at = mob.blockPosition();
        if (habitat.needsSky() && !level.canSeeSky(at.above())) {
            return false;
        }
        if (habitat.biomes().isPresent() && !level.getBiome(at).is(habitat.biomes().get())) {
            return false;
        }
        if (habitat.blocks().isPresent()) {
            int r = habitat.radius();
            int found = 0;
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
                if (level.getBlockState(pos).is(habitat.blocks().get()) && ++found >= habitat.count()) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public static void tellWrongHabitat(BondableBeast beast) {
        Player owner = beast.resolveBondOwner();
        if (owner != null && owner.distanceToSqr(beast.bondMob()) < 32 * 32) {
            PlayerFeedback.actionBar(owner, Component.translatable("companion.wizards_and_beasts.wrong_habitat",
                    beast.bondMob().getDisplayName()));
        }
    }

    /** The juvenile's identity, set before it enters the world. */
    public static void onBorn(BondableBeast baby, BondableBeast mother, BondableBeast father) {
        CompanionProfile profile = Companions.profile(baby);
        if (profile == null) {
            return;
        }
        CompanionMemory mum = Companions.memory(mother);
        CompanionMemory dad = Companions.memory(father);
        float mutation = profile.breeding().map(CompanionProfile.Breeding::mutationChance).orElse(0.15f);
        Random random = new Random(baby.bondMob().getUUID().getMostSignificantBits());
        List<String> traits = CompanionRules.inherit(mum.traits(), dad.traits(), profile.traitPool(),
                profile.traitsPerCreature(), mutation, random, id -> CompanionData.trait(id) != null);
        long day = Companions.day(mother);
        List<String> parents = new ArrayList<>(List.of(nameOf(mother), nameOf(father)));
        CompanionMemory memory = CompanionMemory.EMPTY
                .withTraits(traits)
                .withLineage(Math.max(mum.generation(), dad.generation()) + 1, parents, day)
                .remember(day, "companion.wizards_and_beasts.history.born", String.join(" & ", parents));
        Companions.setMemory(baby, memory);
        Companions.setMemory(mother, mum.remember(day, "companion.wizards_and_beasts.history.raised_young", ""));
        Companions.setMemory(father, dad.remember(day, "companion.wizards_and_beasts.history.raised_young", ""));
        Companions.refreshRoster(baby);
    }

    /** A creature's name for a family tree: its given name, or its species. */
    static String nameOf(BondableBeast beast) {
        PathfinderMob mob = beast.bondMob();
        return mob.hasCustomName() && mob.getCustomName() != null
                ? mob.getCustomName().getString()
                : mob.getType().getDescription().getString();
    }
}
