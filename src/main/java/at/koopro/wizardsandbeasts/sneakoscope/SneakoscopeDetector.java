package at.koopro.wizardsandbeasts.sneakoscope;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.darkartefact.IHorcruxVessel;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandAllegiance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What a Sneakoscope is actually looking for.
 *
 * <p>It is not a detector of <em>hidden things</em>; a charm that showed you every invisible entity
 * in twelve blocks would be a potion effect with extra steps, and would make the Invisibility Cloak
 * worthless the moment one of these turned up in a chest. It is a detector of <em>deceit</em>: of
 * people who are not what they are presenting themselves as. Concealment counts, but so does
 * carrying someone else's wand, wearing the Mark, or having a piece of a torn soul in your pocket —
 * none of which are hidden at all.
 *
 * <p>The holder is never their own suspect. A Sneakoscope that screamed at its owner for putting a
 * cloak on would be unusable by exactly the people who need one.
 */
@NullMarked
public final class SneakoscopeDetector {

    /** Scoreboard tag that marks an entity as a Death Eater to the glass. */
    public static final String DARK_MARK_BEARER_TAG = "dark_mark_bearer";

    /**
     * Mod effects that count as magical concealment, looked up by id rather than by reference.
     *
     * <p>Neither exists yet. Resolving them from the registry instead of importing a constant means
     * the day a Disillusionment Charm or a Vanishment lands, every Sneakoscope in every world starts
     * reacting to it without this file being touched — and until then the lookup simply finds
     * nothing and the rule sits dormant. A hard reference would not compile; a hard-coded
     * {@code false} would quietly rot.
     */
    private static final Identifier[] CONCEALMENT_EFFECT_IDS = {
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "disillusionment"),
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vanish"),
    };

    /**
     * Squared distance a creative/spectator player must cover in one tick to read as "moving".
     *
     * <p>Sized off a walk, not off zero: floating-point drift and mount interpolation both nudge a
     * standing player by a hair, and a Sneakoscope that spins at a motionless spectator is a
     * Sneakoscope that spins forever.
     */
    private static final double MOVEMENT_EPSILON_SQR = 0.0016; // (0.04 blocks/tick)^2

    private SneakoscopeDetector() {}

    /**
     * Sweeps {@code radius} blocks around {@code holder} and reports what the glass makes of it.
     *
     * <p>Server-side only — every rule below reads state the client either does not have or is
     * allowed to lie about.
     */
    public static SneakoscopeReading scan(Player holder, double radius) {
        Level level = holder.level();
        AABB box = holder.getBoundingBox().inflate(radius);
        double radiusSqr = radius * radius;

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate != holder
                        && candidate.isAlive()
                        && candidate.distanceToSqr(holder) <= radiusSqr);

        int threats = 0;
        LivingEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            if (!isSuspicious(candidate, holder)) {
                continue;
            }
            threats++;
            double distanceSqr = candidate.distanceToSqr(holder);
            if (distanceSqr < nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = candidate;
            }
        }

        int bearing = nearest == null
                ? SneakoscopeTuning.NO_BEARING
                : SneakoscopeTuning.bearingSector(nearest.getX() - holder.getX(), nearest.getZ() - holder.getZ());
        return new SneakoscopeReading(threats, bearing);
    }

    /** Whether one candidate trips the glass, from the point of view of {@code holder}. */
    public static boolean isSuspicious(LivingEntity candidate, Player holder) {
        if (candidate == holder) {
            return false;
        }
        if (bearsTheDarkMark(candidate) || carriesAnIntactHorcrux(candidate) || isConcealedByMagic(candidate)) {
            return true;
        }
        if (candidate instanceof Player player) {
            // isInvisible() rather than hasEffect(INVISIBILITY): the Invisibility Cloak sets the
            // flag directly and applies no potion effect at all, and a Sneakoscope that could be
            // walked past under a Cloak is not a Sneakoscope.
            return player.isInvisible()
                    || isMovingUnobserved(player)
                    || wieldsAnotherWizardsWand(player);
        }
        return false;
    }

    /** A mod concealment charm, if one is registered — see {@link #CONCEALMENT_EFFECT_IDS}. */
    private static boolean isConcealedByMagic(LivingEntity candidate) {
        for (Identifier id : CONCEALMENT_EFFECT_IDS) {
            Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(id);
            if (effect.isPresent() && candidate.hasEffect(effect.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A creative or spectating player who is actually going somewhere.
     *
     * <p>Standing still in creative is a builder taking a break; moving through a room nobody can
     * interact with you in is the single most common way a player is watched without knowing it,
     * and it is exactly what the top was made to catch. Idle is excluded so an AFK operator is not
     * a permanent alarm.
     */
    private static boolean isMovingUnobserved(Player player) {
        if (!player.isSpectator() && !player.isCreative()) {
            return false;
        }
        double dx = player.getX() - player.xOld;
        double dy = player.getY() - player.yOld;
        double dz = player.getZ() - player.zOld;
        return dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON_SQR;
    }

    /** Holding a wand that has already sworn itself to somebody else. */
    private static boolean wieldsAnotherWizardsWand(Player player) {
        return isBorrowedWand(player.getMainHandItem(), player.getUUID())
                || isBorrowedWand(player.getOffhandItem(), player.getUUID());
    }

    private static boolean isBorrowedWand(ItemStack stack, UUID wielder) {
        if (!(stack.getItem() instanceof WandItem)) {
            return false;
        }
        WandAllegiance allegiance = stack.get(ModDataComponents.WAND_ALLEGIANCE.get());
        return allegiance != null && allegiance.isBound() && !wielder.equals(allegiance.boundPlayer());
    }

    /**
     * Branded a Death Eater — either through the attachment the Dark Mark brand sets, or through the
     * {@value #DARK_MARK_BEARER_TAG} scoreboard tag, which is how a datapack or a map-maker marks a
     * mob the mod's own branding never touches.
     */
    private static boolean bearsTheDarkMark(LivingEntity candidate) {
        if (candidate.getTags().contains(DARK_MARK_BEARER_TAG)) {
            return true;
        }
        return candidate instanceof Player player && player.getData(ModAttachments.DARK_MARK.get());
    }

    /**
     * Carrying a Horcrux whose soul fragment is still whole. A destroyed vessel is inert and does
     * not spin the glass, matching {@code HorcruxBearerTickHandler}: what the top reacts to is the
     * fragment, not the trinket.
     *
     * <p>Hands and worn equipment for anything alive; the full pack as well for players, because a
     * locket in a rucksack is still being carried and hiding it is precisely the behaviour worth
     * catching.
     */
    private static boolean carriesAnIntactHorcrux(LivingEntity candidate) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (isIntactHorcrux(candidate.getItemBySlot(slot))) {
                return true;
            }
        }
        if (candidate instanceof Player player) {
            for (ItemStack carried : player.getInventory().getNonEquipmentItems()) {
                if (isIntactHorcrux(carried)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIntactHorcrux(ItemStack stack) {
        return stack.getItem() instanceof IHorcruxVessel vessel && vessel.isSoulIntact(stack);
    }
}
