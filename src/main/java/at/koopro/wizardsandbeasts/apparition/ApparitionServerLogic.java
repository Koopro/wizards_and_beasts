package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AbilityProficiency;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionCharge;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchDamageTypes;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
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

    /** Extra ticks a torn-loose item survives on the ground, so a jump gone wrong is recoverable. */
    private static final int RESIDUE_LIFETIME_TICKS = 6000;

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
     * Whether the player holds a Ministry licence.
     *
     * <p>Not a gate. Unlicensed Apparition is illegal, not impossible — Harry, Ron and Hermione do it
     * throughout <i>Deathly Hallows</i> — so this feeds the miss multiplier and the Trace, and refuses
     * nothing. See {@code SplinchResolver.UNLICENSED_MULTIPLIER}.
     */
    public static boolean isLicensed(ServerPlayer player) {
        return PlayerAbilityHelper.isApparitionLicensed(player);
    }

    /** Elf-magic Apparates without a test, a licence, a wizard heritage, or regard for wards that bind wizards. */
    private static boolean isElfApparition(ServerPlayer player) {
        return SkillSystemAPI.hasAbility(player, "elf_apparition");
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
    public static boolean canBeginAttempt(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return false;
        }
        if (!canApparate(player)) {
            fail(player, "apparition.wizards_and_beasts.fail.untrained");
            return false;
        }
        if (isSplinched(player)) {
            fail(player, "apparition.wizards_and_beasts.fail.splinched");
            return false;
        }
        int cooldown = PlayerAbilityHelper.getApparitionCooldownTicks(player);
        if (cooldown > 0) {
            fail(player, "apparition.wizards_and_beasts.fail.cooldown");
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        // Origin ward. A ward at either end fails the attempt cleanly — no cooldown, no splinch.
        if (isWarded(level, player, player.getBoundingBox())) {
            fail(player, "apparition.wizards_and_beasts.fail.warded_origin");
            return false;
        }
        return true;
    }

    /**
     * The floor on the Deliberation window. Proficiency widens the window from here; a skill node can raise
     * the floor itself, so a trained-but-unpractised wizard still gets a usable moment to let go in.
     */
    public static int windowFloorTicks(ServerPlayer player) {
        return ApparitionWindow.BASE_FLOOR_TICKS;
    }

    // ── entry points ──

    /**
     * Aimed, line-of-sight Apparition. Begins a {@link ApparitionTier#BLINK} charge; the destination is
     * whatever the server's own raycast resolves at the moment of release, so the position the client aimed
     * at is a preview and never an instruction.
     */
    public static void handleRequest(ServerPlayer caster, net.minecraft.core.BlockPos targetBlockPos,
                                     Vec3 targetPosition) {
        ApparitionChargeManager.begin(caster, ApparitionTier.BLINK, null);
    }

    /**
     * Apparition to a memorised {@link ApparitionPoint}. Begins a {@link ApparitionTier#ANCHORED} charge —
     * seventy ticks the wizard has to hold together, which is why a known destination is a journey and a
     * blink is a step.
     */
    public static void travelTo(ServerPlayer caster, ApparitionPoint point) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(point.dimension())) {
            fail(caster, "apparition.wizards_and_beasts.fail.other_world");
            return;
        }
        ApparitionChargeManager.begin(caster, ApparitionTier.ANCHORED, point);
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
        Vec3 origin = caster.position();
        Vec3 destination = charge.destination();

        // An attempt that never found a viable spot simply never happened: nothing to arrive at, nothing to
        // be torn by. Invalid targets do not lock, so they cannot splinch you either.
        if (destination == null) {
            fail(caster, "apparition.wizards_and_beasts.fail.no_destination");
            return;
        }
        if (!isElfApparition(caster)
                && isWarded(level, caster, boundsAt(caster, destination))) {
            fail(caster, "apparition.wizards_and_beasts.fail.warded_destination");
            return;
        }

        @Nullable Player passenger = findSideAlongPassenger(caster);
        Destabilization effective = passenger == null ? destabilization : destabilization.asSideAlong();
        SplinchTier tier = SplinchResolver.resolve(missTicks, effective);

        applyOutcome(caster, level, charge, tier, origin, destination);
        if (passenger instanceof ServerPlayer sideAlong) {
            // Both parties splinch at the same tier — Yaxley does not get a gentler landing than Hermione.
            applyOutcome(sideAlong, level, charge, tier, sideAlong.position(), destination);
        }

        caster.causeFoodExhaustion(charge.tier().exhaustion());
        PlayerAbilityHelper.setApparitionCooldownTicks(caster,
                Math.max(charge.tier().cooldownTicks(), tier.lockoutTicks()));
        recordProficiency(caster, tier, origin, destination);
        reportUnlicensed(caster);
        ApparitionBroadcast.get().onResolved(caster, charge.tier(), tier, origin,
                tier.arrives() ? destination : null);
    }

    private static void applyOutcome(ServerPlayer player, ServerLevel level, ApparitionCharge charge,
                                     SplinchTier tier, Vec3 origin, Vec3 destination) {
        if (tier.arrives()) {
            player.teleportTo(destination.x, destination.y, destination.z);
            ApparitionPoint anchor = charge.anchor();
            if (anchor != null) {
                // The saved facing, so a long journey does not end with the wizard spun around.
                player.setYRot(anchor.yaw());
            }
        }
        playArrival(level, origin, tier.arrives() ? destination : origin);

        if (!tier.isSplinch()) {
            return;
        }
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
     */
    private static void dropResidue(ServerPlayer player, ServerLevel level, SplinchTier tier, Vec3 origin) {
        List<Integer> occupied = new ArrayList<>();
        int size = player.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
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

    private static @Nullable Player findSideAlongPassenger(ServerPlayer caster) {
        AABB area = caster.getBoundingBox().inflate(1.5);
        for (Player candidate : caster.level().getEntitiesOfClass(Player.class, area,
                p -> p != caster && p.isShiftKeyDown())) {
            return candidate;
        }
        return null;
    }

    private static void fail(ServerPlayer player, String translationKey) {
        player.displayClientMessage(
                Component.translatable(translationKey).withStyle(ChatFormatting.RED), true);
    }

    private static void playArrival(ServerLevel level, Vec3 origin, Vec3 destination) {
        level.playSound(null, net.minecraft.core.BlockPos.containing(origin),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.9f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(destination),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        spawnBurst(level, origin);
        spawnBurst(level, destination);
    }

    private static void spawnBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0, pos.z, 40, 0.3, 0.5, 0.3, 0.1);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                pos.x, pos.y + 1.0, pos.z, 15, 0.25, 0.25, 0.25, 0.01);
    }
}
