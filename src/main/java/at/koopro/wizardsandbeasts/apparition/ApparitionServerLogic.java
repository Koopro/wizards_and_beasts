package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AbilityProficiency;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionCharge;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
import at.koopro.wizardsandbeasts.apparition.sidealong.SideAlongService;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchDamageTypes;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTags;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rules of Apparition. Owns the gates, the outcome of an attempt and the arrival; the timing itself lives
 * in {@link ApparitionChargeManager}, which is the server's own clock.
 *
 * <p>Splinching is no longer a die roll. A wizard who lets go at the right moment arrives whole every time,
 * and one who panics is torn by exactly as much as they panicked — see {@link SplinchResolver}.
 */
public final class ApparitionServerLogic {


    private ApparitionServerLogic() {
    }

    // ── gates ──

    /**
     * Whether the player has passed their Apparition test — the "unlocked" half of the wizard gate.
     * Two OR'd sources, never one replacing the other: the {@code apparition_training} wandlore skill
     * node (the survival path — a purchased node that read nowhere was a dead end for a capped point),
     * and {@link PlayerAbilityHelper#isApparitionUnlocked} set by the debug/admin command
     * ({@code ApparitionCommands}), which stays a working affordance.
     */
    private static boolean hasApparitionUnlock(ServerPlayer player) {
        return PlayerAbilityHelper.isApparitionUnlocked(player)
                || SkillSystemAPI.hasAbility(player, "apparition_training");
    }

    /**
     * Whether the player holds a Ministry licence — by examination, or on paper.
     *
     * <p>Two OR'd sources, neither replacing the other: the {@code apparitionLicensed} flag
     * {@link at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence} sets on passing the test,
     * and a valid {@link LicenseType#APPARITION} scroll carried in the pack. A physical licence that
     * the Apparition system did not recognise would be a prop.
     *
     * <p>Whether this is a <i>gate</i> depends on the Ministry. On its own it is not: unlicensed
     * Apparition is illegal, not impossible — Harry, Ron and Hermione do it throughout
     * <i>Deathly Hallows</i> — so it feeds the miss multiplier and the Trace. With
     * {@link Module#MINISTRY} switched on, {@link #canBeginAttempt} refuses outright; see there.
     */
    public static boolean isLicensed(ServerPlayer player) {
        return PlayerAbilityHelper.isApparitionLicensed(player)
                || MinistryLicences.has(player, LicenseType.APPARITION);
    }

    /** Elf-magic Apparates without a test, a licence, a wizard heritage, or regard for wards that bind wizards. */
    private static boolean isElfApparition(ServerPlayer player) {
        return SkillSystemAPI.hasAbility(player, "elf_apparition");
    }

    /**
     * Whether the wizard has been taught to Apparate — the "trained" half of the gate, without the
     * heritage check {@link #canApparate} folds in. Read by
     * {@code at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence}, which needs to tell an
     * untrained wizard apart from one whose heritage bars them.
     */
    public static boolean hasTraining(ServerPlayer player) {
        return hasApparitionUnlock(player);
    }

    /** Whether this player Apparates by elf-magic; see {@link #isElfApparition}. */
    public static boolean isElfMagic(ServerPlayer player) {
        return isElfApparition(player);
    }

    /**
     * Whether the player is <i>permitted</i> to Apparate at all. Read-only; used by the ability grant layer to
     * decide wheel visibility. {@link #canBeginAttempt} remains the authority and re-runs every check.
     */
    public static boolean canApparate(ServerPlayer player) {
        return isElfApparition(player) || (hasApparitionUnlock(player) && isAllowedHeritage(player));
    }

    /**
     * The full gate, transient checks included. Called once when an attempt begins; a ward, a cooldown or an
     * existing splinch stops it here, before any charge exists to burn.
     */
    /**
     * The gate itself, as an answer rather than as a side effect.
     *
     * <p>Says nothing to the player: {@link #announce} does that, and {@link ApparitionService} is what puts
     * the two together. Split apart so a caller can ask why without triggering a message, and so the message
     * for a reason lives on the reason.
     */
    public static ApparitionStartResult evaluateStart(ServerPlayer player) {
        // Apparition's own switch, and the ability framework that delivers its keybind. Both, because they
        // gate different things: an operator turning off every player ability should not leave Apparition
        // running, and an operator turning off Apparition alone should not have to disable Legilimency and
        // the Animagus form to do it.
        if (!ModuleManager.isEnabled(Module.APPARITION)
                || !ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return ApparitionStartResult.REJECTED_MODULE_OFF;
        }
        if (!canApparate(player)) {
            return ApparitionStartResult.REJECTED_UNTRAINED;
        }
        if (isSplinched(player)) {
            return ApparitionStartResult.REJECTED_SPLINCHED;
        }
        // Papers. Only a gate while the Ministry is switched on, which is the line
        // ApparitionLicence's own header draws: earning a licence must never depend on the Ministry
        // module, but the Ministry is exactly what *cares* that you hold one. With MINISTRY off the
        // canon behaviour is unchanged — the jump goes through, taxed by the Trace and the splinch
        // multiplier — and elf-magic is never asked for paperwork it was never issued.
        if (TraceService.isActive() && !isElfApparition(player) && !isLicensed(player)) {
            return ApparitionStartResult.REJECTED_UNLICENSED;
        }
        if (PlayerAbilityHelper.getApparitionCooldownTicks(player) > 0) {
            return ApparitionStartResult.REJECTED_COOLDOWN;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ApparitionStartResult.REJECTED_MODULE_OFF;
        }
        // Origin ward. A ward at either end fails the attempt cleanly — no cooldown, no splinch.
        if (isWarded(level, player, player.getBoundingBox())) {
            return ApparitionStartResult.REJECTED_WARDED_ORIGIN;
        }
        return ApparitionStartResult.STARTED;
    }

    /** Tells the player what a result means, if it means anything worth saying. */
    public static void announce(ServerPlayer player, ApparitionStartResult result) {
        String key = result.messageKey();
        if (key == null) {
            return;
        }
        if (result.isTransientMessage()) {
            failTransient(player, key);
        } else {
            fail(player, key);
        }
    }

    /**
     * How far a journey has to be before arriving from it turns the stomach.
     *
     * <p>Above the blink range a practised wizard has ({@code 12..30} blocks), so this is a mark of distance
     * travelled rather than a tax on every jump.
     */
    private static final double TRAVEL_SICKNESS_DISTANCE = 64.0;
    /** Two seconds of it. Long enough to feel, too short to fight in. */
    private static final int TRAVEL_SICKNESS_TICKS = 40;

    /**
     * The sputter: what a jump costs when it was held to the end and then had nowhere to go.
     *
     * <p>Deliberately shorter than any success and far shorter than a splinch lockout, and deliberately
     * charged <b>only</b> on failures that happen at the far end of a charge — a destination that never
     * resolved, or one that turned out to be warded. The refusals in {@link #canBeginAttempt} charge nothing
     * at all: declining to start something and then penalising the player for it is how a gate becomes a
     * punishment.
     */
    // Package-private rather than private so the acceptance test can assert the three cooldown lengths are
    // actually three lengths. It is a constant, not a seam: nothing writes it.
    static final int FAILED_ATTEMPT_COOLDOWN_TICKS = 30;

    /** Ticks the {@code apparition_deliberation} node adds to the window floor. */
    private static final int DELIBERATION_NODE_TICKS = 3;

    /**
     * The floor on the Deliberation window. Proficiency widens the window from here; the
     * {@code apparition_deliberation} node raises the floor itself, which is the one thing proficiency cannot
     * do — it buys a novice a usable moment to let go in rather than a faster climb toward one.
     */
    public static int windowFloorTicks(ServerPlayer player) {
        return SkillSystemAPI.hasAbility(player, "apparition_deliberation")
                ? ApparitionWindow.BASE_FLOOR_TICKS + DELIBERATION_NODE_TICKS
                : ApparitionWindow.BASE_FLOOR_TICKS;
    }

    // ── entry points ──

    /**
     * Aimed, line-of-sight Apparition. Begins a {@link ApparitionTier#BLINK} charge.
     *
     * <p><b>Takes no target.</b> It used to accept the client's picked block and position and then ignore
     * both, which read as though the client chose the destination. It never did: the charge re-runs the
     * server's own raycast every tick and resolves it at release, so a client-supplied position could only
     * ever be a preview, and carrying one through the signature invited somebody to start trusting it.
     *
     * <p>Beginning with no viable spot in view is deliberate. The Determination clock does not advance until
     * a destination exists ({@link ApparitionCharge#advance()}), so a wizard who starts while facing open sky
     * simply holds, unhurried, until they find somewhere to go.
     */
    public static void handleRequest(ServerPlayer caster) {
        ApparitionService.tryStart(caster);
    }

    /**
     * Apparition to a memorised {@link ApparitionPoint}. Begins a {@link ApparitionTier#ANCHORED} charge —
     * seventy ticks the wizard has to hold together, which is why a known destination is a journey and a
     * blink is a step.
     */
    public static void travelTo(ServerPlayer caster, ApparitionPoint point) {
        ApparitionService.tryStart(caster, point);
    }

    // ── resolution ──

    /**
     * Applies the outcome of a finished attempt: the ladder, the arrival or the failure to arrive, the wound,
     * what was left behind, and the cost.
     *
     * @param missTicks raw miss from the release, before {@link SplinchResolver#inflate} sees it
     */
    public static void completeAttempt(ServerPlayer caster, ApparitionCharge charge, int missTicks,
                                       Destabilization destabilization) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        // Where the attempt began, not where the wizard drifted to while holding it — see
        // ApparitionCharge#startPosition. This is the crack the neighbours hear and the spot a splinch
        // leaves an arm on.
        Vec3 origin = charge.startPosition();
        Vec3 destination = charge.destination();

        // An attempt nobody ever let go of is not a botched jump; it is a jump that was never made. It used
        // to run the ladder and land on CATASTROPHIC — the harshest rung in the ability, awarded for doing
        // nothing — which made walking away from a charge the single most expensive thing a wizard could do
        // with one. Now it collapses: no arrival, no wound, and the sputter cooldown for the wasted effort.
        if (ApparitionWindow.isForcedDischarge(missTicks)) {
            collapseAttempt(caster, charge);
            return;
        }

        // An attempt that never found a viable spot simply never happened: nothing to arrive at, nothing to
        // be torn by. Invalid targets do not lock, so they cannot splinch you either.
        if (destination == null) {
            failAttempt(caster, "apparition.wizards_and_beasts.fail.no_destination");
            return;
        }
        if (!isElfApparition(caster)
                && isWarded(level, caster, boundsAt(caster, destination))) {
            failAttempt(caster, "apparition.wizards_and_beasts.fail.warded_destination");
            return;
        }

        @Nullable Player passenger = findSideAlongPassenger(caster);
        Destabilization effective = passenger == null ? destabilization : destabilization.asSideAlong();
        // The ladder first, then the under-fire floor. Two different questions, answered in that order:
        // how badly was this released, and was this wizard being shot at while releasing it.
        SplinchTier computed = SplinchResolver.resolve(missTicks, effective);
        SplinchTier tier = SplinchResolver.floorForWindupDamage(
                computed,
                effective.damageInstances(),
                charge.tier() == ApparitionTier.ANCHORED,
                effective.sideAlong(),
                ApparitionRules.windupDamageMode());

        // Captured before anybody is teleported: a passenger's own departure is a second crack, in a
        // different place, and after applyOutcome they are no longer standing in it.
        @Nullable Vec3 passengerOrigin = passenger == null ? null : passenger.position();

        applyOutcome(caster, level, charge, tier, origin, destination);
        if (passenger instanceof ServerPlayer sideAlong) {
            // Both parties splinch at the same tier — Yaxley does not get a gentler landing than Hermione.
            applyOutcome(sideAlong, level, charge, tier, passengerOrigin, destination);
        }

        caster.causeFoodExhaustion(charge.tier().exhaustion());
        PlayerAbilityHelper.setApparitionCooldownTicks(caster,
                Math.max(charge.tier().cooldownTicks(), tier.lockoutTicks()));
        recordProficiency(caster, tier, origin, destination);
        reportUnlicensed(caster);
        // A side-along is one journey, not a standing arrangement.
        SideAlongService.clearPartner(caster);

        // One journey, one signature: the crack belongs to the wizard whose magic this is, so a side-along
        // passenger cracks like their carrier rather than announcing their own proficiency and heritage.
        int radius = ApparitionPresentationBroadcaster.crackRadius(caster);
        ApparitionCrackVariant variant = ApparitionPresentationBroadcaster.crackVariant(caster);
        @Nullable Vec3 arrival = tier.arrives() ? destination : null;

        ApparitionBroadcast.get().onResolved(caster, charge.tier(), tier, origin, arrival, radius, variant);
        if (passenger instanceof ServerPlayer sideAlong && passengerOrigin != null) {
            // Without this the passenger vanishes and arrives in total silence: the caster's own resolution
            // packet is addressed to the people watching the caster, and nobody is watching them.
            ApparitionBroadcast.get().onResolved(
                    sideAlong, charge.tier(), tier, passengerOrigin, arrival, radius, variant);
        }
    }

    private static void applyOutcome(ServerPlayer player, ServerLevel level, ApparitionCharge charge,
                                     SplinchTier tier, Vec3 origin, Vec3 destination) {
        if (tier.arrives()) {
            // Off the broom first. A passenger's position is written by its vehicle every tick, so a
            // teleport that leaves the rider mounted puts them at the destination for exactly one tick and
            // then snaps them back — silently, with the jump's cooldown and exhaustion already charged.
            if (player.isPassenger()) {
                player.stopRiding();
            }
            // And nothing rides them through it either. Same reason the Floo puts passengers down: an
            // entity left attached to somebody who is suddenly a thousand blocks away is a bug looking for
            // a place to happen.
            if (!player.getPassengers().isEmpty()) {
                player.ejectPassengers();
            }
            player.teleportTo(destination.x, destination.y, destination.z);
            // Apparating out of a fall is an escape from it. Without this the accumulated distance is still
            // on the player and the ground they arrive on collects it.
            player.resetFallDistance();
            ApparitionPoint anchor = charge.anchor();
            if (anchor != null) {
                // The saved facing, so a long journey does not end with the wizard spun around.
                player.setYRot(anchor.yaw());
            }
            applyTravelSickness(player, origin, destination);
            ApparitionAdvancements.awardArrival(player);
        }

        if (!tier.isSplinch()) {
            return;
        }
        ApparitionAdvancements.awardSplinch(player);
        if (tier.damage() > 0.0f) {
            player.hurt(level.damageSources().source(SplinchDamageTypes.SPLINCH), tier.damage());
        }
        if (tier.appliesEffect()) {
            player.addEffect(new MobEffectInstance(ModEffects.SPLINCHED, tier.effectTicks(),
                    tier.effectAmplifier(), false, true, true));
        }
        dropResidue(player, level, tier, origin);
        ApparitionBroadcast.get().onResidue(player, origin, tier);
    }

    /**
     * Leaves part of what the wizard was carrying where they started. Ordinary item entities with an extended
     * life, so a bad jump is a scramble back rather than a deletion.
     *
     * <p><b>The pack only.</b> {@code Inventory.getContainerSize()} is 43 in 1.21.11 — the 36 pack slots plus
     * seven equipment ones (the four armour pieces, the off-hand, body and saddle) — so iterating it tore
     * worn robes off a wizard's back and the wand out of their off-hand. Splinching leaves behind what you
     * were <i>carrying</i>; what you are wearing and holding goes through with you.
     *
     * <p>Slots holding {@link SplinchTags#SPLINCH_IMMUNE} are stepped over entirely, which is the hook a pack
     * needs to keep a quest item survivable on a server where losing it cannot be undone.
     */
    private static void dropResidue(ServerPlayer player, ServerLevel level, SplinchTier tier, Vec3 origin) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !stack.is(SplinchTags.SPLINCH_IMMUNE)) {
                occupied.add(slot);
            }
        }
        if (occupied.isEmpty()) {
            return;
        }
        Collections.shuffle(occupied, new java.util.Random(player.getRandom().nextLong()));

        int count = tier.fixedItemDrops() > 0
                ? Math.min(tier.fixedItemDrops(), occupied.size())
                : (int) Math.ceil(occupied.size() * tier.inventoryFraction());
        for (int i = 0; i < count; i++) {
            ItemStack stack = player.getInventory().removeItemNoUpdate(occupied.get(i));
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity entity = new ItemEntity(level, origin.x, origin.y + 0.5, origin.z, stack);
            entity.setExtendedLifetime();
            entity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(entity);
        }
    }

    /**
     * Practice. A clean arrival teaches the most, a minor tear teaches a little, and being badly torn teaches
     * nothing at all — the increment scales with how far the wizard actually moved, so a long journey is
     * worth more than a hop.
     */
    private static void recordProficiency(ServerPlayer player, SplinchTier tier, Vec3 origin, Vec3 destination) {
        float share = switch (tier) {
            case CLEAN -> 1.0f;
            case MINOR -> 0.25f;
            case MAJOR, CATASTROPHIC -> 0.0f;
        };
        if (share <= 0.0f) {
            return;
        }
        double distance = origin.distanceTo(destination);
        if (distance < PROFICIENCY_MIN_DISTANCE) {
            return;
        }
        float scaled = (float) Math.min(PROFICIENCY_GRANT_CAP, distance * PROFICIENCY_PER_BLOCK);
        AbilityProficiency.add(player, AbilityIds.APPARITION, scaled * share);
    }

    /**
     * Files an unlicensed jump with the Ministry.
     *
     * <p>The whole enforcement half of this is deliberately absent: no fine, no summons, no patrol, no way to
     * sit the test. The record entry is a landing site for a later prompt, and until then the only thing
     * being unlicensed costs you is the miss multiplier. Elf-magic is outside the licensing regime entirely,
     * and {@code TraceService} itself goes quiet when the Ministry module is off.
     */
    private static void reportUnlicensed(ServerPlayer caster) {
        if (isLicensed(caster) || isElfApparition(caster)) {
            return;
        }
        TraceService.report(caster, MagicalOffence.UNLICENSED_APPARITION);
    }

    /** Proficiency earned per block travelled, before the per-grant cap. */
    private static final float PROFICIENCY_PER_BLOCK = 0.00005f;
    /** Most a single jump can teach, so one cross-continent trip is not a whole career. */
    private static final float PROFICIENCY_GRANT_CAP = 0.01f;
    /**
     * Shorter than this and the jump teaches nothing at all. Without the floor, hopping a wizard's own
     * length on a forty-tick cooldown reached mastery about five times faster than anchored travel, which
     * inverts the intent — the slow, interruptible, expensive jump is meant to be how a wizard gets good,
     * not the cheap one. A blink still earns; it has to be a real blink.
     */
    public static final double PROFICIENCY_MIN_DISTANCE = 15.0;

    // ── ticking ──

    public static void tick(ServerPlayer player) {
        int cooldown = PlayerAbilityHelper.getApparitionCooldownTicks(player);
        if (cooldown > 0) {
            PlayerAbilityHelper.setApparitionCooldownTicks(player, cooldown - 1);
        }
        ApparitionChargeManager.tick(player);
        // Splinching needs no countdown here: the SPLINCHED mob effect owns its own duration, so it expires,
        // shows on the potion HUD and is curable like any other effect.
    }

    /**
     * Whether the player is currently splinched. Reads the {@code SPLINCHED} effect directly — that effect
     * is the single source of truth, so this can never disagree with what the player sees on their HUD.
     */
    public static boolean isSplinched(ServerPlayer player) {
        return player.hasEffect(ModEffects.SPLINCHED);
    }

    // ── helpers ──

    private static boolean isWarded(ServerLevel level, ServerPlayer player, AABB bounds) {
        if (isElfApparition(player)) {
            return false;
        }
        return ApparitionWardRegistry.findBlockingWard(level, player.createCommandSourceStack(), bounds) != null;
    }

    private static AABB boundsAt(ServerPlayer player, Vec3 position) {
        return player.getBoundingBox().move(
                position.x - player.getX(), position.y - player.getY(), position.z - player.getZ());
    }

    private static boolean isAllowedHeritage(ServerPlayer player) {
        Heritage heritage = HeritageAPI.getPlayerHeritage(player);
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(player);
        if (heritage == Heritage.WIZARDKIND) {
            return true;
        }
        if (variant == null) {
            return false;
        }
        // House-elves carry `innate_apparition` — the same concept the `elf_apparition` skill ability is built
        // around. `can_apparate` is the explicit opt-in for anything else that should be able to.
        return variant.hasTag("can_apparate") || variant.hasTag("innate_apparition");
    }

    /**
     * Who is coming too. Two flavours, both resolved at the moment of release and both landing on the same
     * splinch rung as the caster:
     *
     * <ul>
     *   <li>a wizard who accepted an offer and is still standing close enough to be taken;</li>
     *   <li>anyone else in reach who is holding on — the hostile grab, unchanged. Yaxley seizes Hermione
     *       mid-Disapparition in <i>Deathly Hallows</i>, and she is not asked first.</li>
     * </ul>
     */
    private static @Nullable Player findSideAlongPassenger(ServerPlayer caster) {
        AABB area = caster.getBoundingBox().inflate(1.5);
        ServerPlayer partner = SideAlongService.partnerOf(caster);
        if (partner != null && partner.getBoundingBox().intersects(area)) {
            return partner;
        }
        for (Player candidate : caster.level().getEntitiesOfClass(Player.class, area,
                p -> p != caster && p.isShiftKeyDown())) {
            return candidate;
        }
        return null;
    }

    /**
     * A refused attempt, told to the player with its reason.
     *
     * <p>A toast rather than the action bar, per {@link PlayerFeedback}'s split: every one of these is
     * a refusal the player needs the <em>reason</em> for, and the action bar holds one line that the
     * next thing to write overwrites. The exception is {@link #failTransient}, for the cooldown —
     * that one is worth saying now and worthless a second later, which is what the action bar is for.
     */
    private static void fail(ServerPlayer player, String reasonKey) {
        PlayerFeedback.refuse(player,
                Component.translatable("apparition.wizards_and_beasts.fail.title"),
                Component.translatable(reasonKey));
    }

    /**
     * A charge that ran its course and came to nothing: the message, plus the sputter cooldown.
     *
     * <p>Distinct from {@link #fail}, which is for the gates. Three lengths, as they should be — a success
     * costs the tier's own cooldown, a splinch costs its lockout, and getting all the way to the end with
     * nowhere to arrive costs {@link #FAILED_ATTEMPT_COOLDOWN_TICKS}.
     */
    private static void failAttempt(ServerPlayer player, String reasonKey) {
        fail(player, reasonKey);
        PlayerAbilityHelper.setApparitionCooldownTicks(player, FAILED_ATTEMPT_COOLDOWN_TICKS);
    }

    /**
     * A charge held past its hard cap and never released. Nothing arrives and nothing tears.
     *
     * <p>Deliberately not a splinch. Splinching is what a <em>botched jump</em> does to a body, and the
     * three Ds have to have been attempted for one to be botched — a release, however badly timed, is an
     * attempt. Standing there until the magic gutters out is a wizard who lost their nerve, and canon has
     * nothing to say about that beyond it not working.
     *
     * <p>Not free either: the cooldown is charged, so a player cannot use an unreleased hold as a way to
     * keep an attempt permanently available, and the exhaustion is charged because the effort was spent
     * whether or not it went anywhere.
     */
    private static void collapseAttempt(ServerPlayer player, ApparitionCharge charge) {
        player.causeFoodExhaustion(charge.tier().exhaustion());
        PlayerAbilityHelper.setApparitionCooldownTicks(player, FAILED_ATTEMPT_COOLDOWN_TICKS);
        fail(player, "apparition.wizards_and_beasts.fail.collapsed");
        // Same teardown an abort does: without an IDLE phase the motes and the destination ring hang on
        // every client watching until something else happens to this player.
        ApparitionBroadcast.get().onPhaseChange(player, charge.tier(), ApparitionPhase.IDLE,
                charge.elapsed(), charge.windowOpen(), charge.windowClose());
    }

    /** A refusal that resolves on its own in a moment; see {@link #fail}. */
    private static void failTransient(ServerPlayer player, String reasonKey) {
        PlayerFeedback.actionBar(player,
                Component.translatable(reasonKey).withStyle(ChatFormatting.RED));
    }

    /**
     * The lurch of a long journey.
     *
     * <p>Squeezing a body through space costs something at the far end, and canon is consistent that the
     * effect is nausea rather than injury. Kept short and unamplified: this is the moment of arrival being
     * unpleasant, not a debuff to play around, and a blink across a courtyard is under the threshold and
     * costs nothing at all.
     *
     * <p>Elf-magic is exempt. An elf crosses the country without ceremony, which is the whole point of it.
     */
    private static void applyTravelSickness(ServerPlayer player, Vec3 origin, Vec3 destination) {
        if (isElfApparition(player) || origin.distanceTo(destination) < TRAVEL_SICKNESS_DISTANCE) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                MobEffects.NAUSEA, TRAVEL_SICKNESS_TICKS, 0, false, false, true));
    }
}
