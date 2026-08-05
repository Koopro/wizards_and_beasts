package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AbilityProficiency;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionPresentationS2CPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Turns Apparition mechanics into packets. The only implementation of {@link ApparitionEventBroadcaster};
 * installed once at setup and never swapped at runtime.
 *
 * <p>This class exists to be the <b>only</b> place proficiency and heritage are consulted for presentation.
 * Both are resolved here into an audible radius and a crack variant, and only those two decisions are sent —
 * an observer's client never learns how practised the caster is or what they actually are.
 */
@NullMarked
public final class ApparitionPresentationBroadcaster implements ApparitionEventBroadcaster {

    /** Audible radius at proficiency 0. */
    public static final int MAX_CRACK_RADIUS = 32;
    /** Audible radius at mastery. Never zero: Apparition is quiet at its best, never silent. */
    public static final int MIN_CRACK_RADIUS = 8;

    /**
     * Proficiency at which the crack changes character rather than merely carrying less far.
     *
     * <p>Not a canon figure — canon says nothing about a muffled crack. Chosen so the timbre change lands
     * near the top of the curve, where it reads as a mastery payoff rather than an early freebie.
     */
    public static final float MUFFLED_THRESHOLD = 0.75f;

    /** The form id a house-elf presents as. Matches {@code HeritageFormBridge}'s mapping for HOUSE_ELF. */
    private static final String ELF_FORM_ID = "house_elf_default";

    private ApparitionPresentationBroadcaster() {}

    public static final ApparitionPresentationBroadcaster INSTANCE = new ApparitionPresentationBroadcaster();

    // ── derivations: the two things that leave this class ──

    /**
     * How far the crack carries. Scales {@link #MAX_CRACK_RADIUS} down to {@link #MIN_CRACK_RADIUS} across the
     * proficiency curve — a real multiplayer stealth lever, and the reason the floor is not zero.
     */
    public static int crackRadius(ServerPlayer player) {
        float proficiency = AbilityProficiency.get(player, AbilityIds.APPARITION);
        float clamped = Math.max(0.0f, Math.min(1.0f, proficiency));
        return MAX_CRACK_RADIUS - Math.round(clamped * (MAX_CRACK_RADIUS - MIN_CRACK_RADIUS));
    }

    /**
     * Which crack an observer hears.
     *
     * <p>Keyed to the caster's <b>apparent</b> form. A disguised elf cracks like a wizard and a human wearing
     * an elf's face cracks like an elf, because the alternative hands every bystander a disguise detector.
     * Elf-ness wins over muffling: the elf crack is a signature of what you are, while muffling is a mark of
     * how well you do it, and an elf still gets the quieter radius from the same practice.
     */
    public static ApparitionCrackVariant crackVariant(ServerPlayer player) {
        if (isApparentlyElf(player)) {
            return ApparitionCrackVariant.ELF;
        }
        return AbilityProficiency.get(player, AbilityIds.APPARITION) >= MUFFLED_THRESHOLD
                ? ApparitionCrackVariant.MUFFLED
                : ApparitionCrackVariant.WIZARD;
    }

    /**
     * Whether the caster <i>looks</i> like a house-elf right now. An active disguise is authoritative in both
     * directions; true heritage is consulted only when nothing is worn over it.
     */
    private static boolean isApparentlyElf(ServerPlayer player) {
        String disguise = PlayerAbilityHelper.getCurrentDisguiseFormId(player);
        if (disguise != null) {
            return ELF_FORM_ID.equals(disguise);
        }
        return HeritageAPI.getPlayerHeritage(player) == Heritage.HOUSE_ELF;
    }

    // ── broadcast ──

    @Override
    public void onChargeBegin(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                              int windowOpen, int windowClose) {
        sendCharge(player, tier, phase, 0, windowOpen, windowClose);
    }

    @Override
    public void onChargeTick(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                             int elapsed, int windowOpen, int windowClose) {
        sendCharge(player, tier, phase, elapsed, windowOpen, windowClose);
    }

    @Override
    public void onPhaseChange(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                              int elapsed, int windowOpen, int windowClose) {
        sendCharge(player, tier, phase, elapsed, windowOpen, windowClose);
    }

    @Override
    public void onResolved(ServerPlayer player, ApparitionTier tier, SplinchTier splinchTier,
                           Vec3 origin, @Nullable Vec3 destination,
                           int radius, ApparitionCrackVariant crackVariant) {
        ApparitionPresentationS2CPayload payload = new ApparitionPresentationS2CPayload(
                player.getId(), tier, ApparitionPhase.RESOLVING, 0, 0, 0,
                splinchTier, origin, destination, radius, crackVariant);

        // Observers at the origin: everyone already tracking the caster, plus the caster themselves.
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);

        // Observers at the destination are a different set of people entirely — that is the whole point of
        // splitting this by audience — and they are usually outside the caster's tracking range.
        if (destination != null && player.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersNear(level, player,
                    destination.x, destination.y, destination.z, radius, payload);
        }
    }

    /**
     * Charge updates go to the caster and to anyone already tracking them. That audience is exactly right: the
     * inward motes are the counter-play telegraph for an anchored jump, so the people who need to see them are
     * the people close enough to interrupt it.
     */
    private static void sendCharge(ServerPlayer player, ApparitionTier tier, ApparitionPhase phase,
                                   int elapsed, int windowOpen, int windowClose) {
        // The destination the server resolved, not the client's own guess at the same raycast. Without it the
        // ring can be perfectly on time and still be drawn in the wrong place.
        Vec3 destination = ApparitionChargeManager.destinationOf(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ApparitionPresentationS2CPayload(
                player.getId(), tier, phase, elapsed, windowOpen, windowClose,
                null, player.position(), destination, crackRadius(player), crackVariant(player)));
    }
}
