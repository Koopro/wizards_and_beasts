package at.koopro.wizardsandbeasts.creature.bond;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.MagizoologyXPEvent;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.util.MagizoologyHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A creature that can form a relationship with a player.
 *
 * <p>Extracted from the Niffler, which was the only creature in the mod with one. The pieces the
 * Niffler had woven into its own class — an owner, a 0–100 bond, a feed table with cooldowns, the
 * Magizoology bonus, milestone XP events, and a follow goal gated on the bond — are all here, and
 * the numbers behind them moved out into a datapack {@link BondProfile}. The Niffler keeps its
 * pouch, its pocket-carry and its theft; those are the Niffler, not the bond.
 *
 * <h2>Implementing this</h2>
 * Every implementor is a {@link PathfinderMob}; the default methods rely on it, exactly as vanilla's
 * {@code NeutralMob} relies on its implementors being {@code Mob}. An implementor supplies three
 * things and inherits the rest:
 *
 * <ol>
 *   <li>{@link #bondState()} — a {@code BondState} field it owns and persists;</li>
 *   <li>{@link #setSyncedBondLevel(int)} / {@link #getSyncedBondLevel()} — its own
 *       {@code EntityDataAccessor<Integer>}, because a synched accessor must be defined on the
 *       concrete class that uses it;</li>
 *   <li>calls to {@link #tickBond()}, {@link #offerBondFood}, {@link #onBondedHurt} and the two
 *       save hooks from the corresponding entity methods.</li>
 * </ol>
 *
 * <h2>Server authority</h2>
 * Every method here mutates only on the server and returns early on a client level. The client is
 * told the bond level through the implementor's synched accessor and is never asked what it thinks
 * the bond is — a client that lies about its bond gets nothing, because no branch reads the synched
 * value to make a decision.
 *
 * <h2>Module gating</h2>
 * Gates access, never registration, matching {@code GenericBeastEntity}: with
 * {@code Module.CREATURES} off the interaction passes through and the tick does nothing, but the
 * state is still loaded, saved and synced, so toggling the module back on restores every bond
 * rather than resetting it.
 */
@NullMarked
public interface BondableBeast {

    /** The bond storage this creature owns. Never null; a creature with no profile simply never fills it. */
    BondState bondState();

    /** Write {@code level} into this entity's own synched data slot. */
    void setSyncedBondLevel(int level);

    /** Read this entity's synched bond level. The client's only view of the bond. */
    int getSyncedBondLevel();

    /**
     * This creature, as the mob it is.
     *
     * <p>Same contract and same cast as vanilla's {@code NeutralMob}: implementing this interface on
     * anything that is not a {@link PathfinderMob} is a programming error, and one that fails loudly
     * at the first call rather than silently misbehaving.
     */
    default PathfinderMob bondMob() {
        return (PathfinderMob) this;
    }

    /** Registry id of this creature — also the key its {@link BondProfile} is filed under. */
    default Identifier bondSpecies() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(bondMob().getType());
    }

    /** This species' profile, or {@code null} when no datapack declares one and it does not bond. */
    @Nullable
    default BondProfile bondProfile() {
        return BondProfileRegistry.get(bondSpecies());
    }

    /** True when this species bonds at all — the presence of a profile is the whole opt-in. */
    default boolean canBond() {
        return bondProfile() != null;
    }

    default int bondLevel() {
        return bondState().level();
    }

    @Nullable
    default UUID bondOwner() {
        return bondState().ownerUUID();
    }

    /** The bonded owner if they are loaded in this level, else {@code null}. */
    @Nullable
    default Player resolveBondOwner() {
        UUID owner = bondState().ownerUUID();
        return owner == null ? null : bondMob().level().getPlayerByUUID(owner);
    }

    /** True once the bond is deep enough for this species to follow its owner about. */
    default boolean followsOwner() {
        BondProfile profile = bondProfile();
        return profile != null && bondLevel() >= profile.followThreshold();
    }

    // ── feeding ──────────────────────────────────────────────────────────────

    /**
     * Handle a player offering an item in hand.
     *
     * <p>Returns {@link InteractionResult#PASS} for anything this species does not eat, so a caller
     * can chain it ahead of its own interactions without swallowing them — which is what lets the
     * Bowtruckle keep being tempted by sticks it is also fed.
     */
    default InteractionResult offerBondFood(Player player, InteractionHand hand) {
        BondProfile profile = bondProfile();
        if (profile == null || !ModuleManager.isEnabled(Module.CREATURES)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        BondFeed feed = profile.feedFor(held);
        if (feed == null) {
            return InteractionResult.PASS;
        }
        // Past this point the offering was accepted in principle, so the client must not run the
        // logic — it returns SUCCESS to swing the arm and waits to be told the outcome.
        if (bondMob().level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (bondState().feedCooldown() > 0) {
            playBondSound(profile.refuseSound(), 0.7f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        // Read the item before the stack is spent: the same offering can be both a feed and the
        // breeding item, and after shrink(1) an empty stack no longer knows what it was.
        Item offered = held.getItem();
        if (!player.isCreative()) {
            held.shrink(1);
        }
        bondState().setFeedCooldown(feed.cooldownSeconds() * 20);
        increaseBond(player, feed.gain(), true);
        playBondSound(profile.feedSound(), 1.0f, 1.0f);
        tryEnterLove(profile, offered);
        return InteractionResult.SUCCESS;
    }

    // ── bond movement ────────────────────────────────────────────────────────

    /**
     * Raise the bond, claiming the creature for {@code player} if it is unowned.
     *
     * @param fireXp whether crossing a milestone should award Magizoology XP. False for the slow
     *               drip of simply being nearby, which would otherwise pay out the same as a rare
     *               treat for doing nothing but standing still.
     */
    default void increaseBond(Player player, int amount, boolean fireXp) {
        BondProfile profile = bondProfile();
        if (profile == null || bondMob().level().isClientSide() || amount <= 0) {
            return;
        }
        if (bondState().ownerUUID() == null) {
            bondState().setOwner(player.getUUID());
        }
        if (MagizoologyHelper.isMagizoologist(player)) {
            amount = (int) Math.ceil(amount * 1.5);
        }

        int before = bondState().level();
        int after = bondState().setLevel(before + amount, profile.maxBond());
        setSyncedBondLevel(after);
        if (after == before) {
            return;
        }

        if (fireXp) {
            for (int milestone : profile.milestones()) {
                if (before < milestone && after >= milestone) {
                    announceMilestone(player, profile, milestone);
                }
            }
        }
        // The mastery mark is not a milestone: it is about the book, not about XP, and it should
        // land whether the bond was earned by feeding or by keeping company.
        profile.masteryBond().ifPresent(mastery -> {
            if (before < mastery && after >= mastery) {
                BestiaryDataHelper.setTier(player, bondSpecies(), DiscoveryTier.MASTERED);
            }
        });
    }

    /** Lower the bond without changing ownership — a betrayal is not a divorce. */
    default void decreaseBond(int amount) {
        BondProfile profile = bondProfile();
        if (profile == null || bondMob().level().isClientSide() || amount <= 0) {
            return;
        }
        setSyncedBondLevel(bondState().setLevel(bondState().level() - amount, profile.maxBond()));
    }

    /**
     * A milestone crossed: XP for the system, and a toast so the player finds out it happened.
     *
     * <p>The Niffler fired the event and said nothing, which meant the four milestones it has were
     * invisible unless you were watching the follow behaviour change.
     */
    private void announceMilestone(Player player, BondProfile profile, int milestone) {
        NeoForge.EVENT_BUS.post(new MagizoologyXPEvent(player, milestone,
                profile.xpTag(bondSpecies(), milestone)));
        PlayerFeedback.unlocked(player,
                Component.translatable("bond.wizards_and_beasts.milestone", bondMob().getDisplayName()),
                Component.translatable("bond.wizards_and_beasts.milestone.detail", milestone));
    }

    /**
     * Called from the entity's hurt path: being struck by your own creature's owner costs bond.
     *
     * <p>Only the owner's blows count. A bonded creature that loses its bond to a passing skeleton
     * would make the whole layer feel arbitrary, and would hand any player a way to grief another's
     * companion.
     */
    default void onBondedHurt(DamageSource source) {
        BondProfile profile = bondProfile();
        if (profile == null || bondMob().level().isClientSide() || profile.betrayalPenalty() <= 0) {
            return;
        }
        UUID owner = bondState().ownerUUID();
        if (owner != null && source.getEntity() instanceof Player player && owner.equals(player.getUUID())) {
            decreaseBond(profile.betrayalPenalty());
        }
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    /**
     * The server-side beat: run the two timers, accrue company, and pay out a gift when one is due.
     *
     * <p>Call it unconditionally from {@code tick()}; it returns immediately on the client and for
     * species with no profile.
     */
    default void tickBond() {
        PathfinderMob mob = bondMob();
        if (mob.level().isClientSide() || !mob.isAlive()) {
            return;
        }
        BondProfile profile = bondProfile();
        if (profile == null || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        bondState().tickTimers();
        // Everything below is a once-a-second question asked of at most one nearby player.
        if (mob.tickCount % 20 != 0) {
            return;
        }
        tickProximityBond(profile);
        tickGift(profile);
        tickBreeding(profile);
        tickGrowth(profile);
    }

    /** Time spent near the owner is worth bond, at the profile's rate. */
    private void tickProximityBond(BondProfile profile) {
        if (profile.proximityGain() <= 0 || profile.proximitySeconds() <= 0) {
            return;
        }
        Player owner = resolveBondOwner();
        double range = profile.proximityRange();
        if (owner == null || !owner.isAlive() || bondMob().distanceToSqr(owner) > range * range) {
            bondState().setProximityTicks(0);
            return;
        }
        int accrued = bondState().proximityTicks() + 1;
        if (accrued < profile.proximitySeconds()) {
            bondState().setProximityTicks(accrued);
            return;
        }
        bondState().setProximityTicks(0);
        increaseBond(owner, profile.proximityGain(), false);
    }

    /** Hand over the species' gift when the bond is deep enough and the cooldown has run out. */
    private void tickGift(BondProfile profile) {
        BondGift gift = profile.gift().orElse(null);
        if (gift == null || bondState().giftCooldown() > 0 || bondLevel() < gift.minBond()) {
            return;
        }
        if (bondState().isJuvenile()) {
            return; // a calf produces nothing; growing up is its whole job
        }
        PathfinderMob mob = bondMob();
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        bondState().setGiftCooldown(gift.cooldownSeconds() * 20);
        ItemStack stack = gift.roll(mob.getRandom());
        mob.spawnAtLocation(serverLevel, stack);
        playBondSound(profile.feedSound(), 0.6f, 1.4f);
    }

    // ── breeding and growth ──────────────────────────────────────────

    /** True while this creature is a scaled-down juvenile that has not finished growing. */
    default boolean isJuvenileBeast() {
        return bondState().isJuvenile();
    }

    /**
     * Being fed the breeding item, at enough bond and off cooldown, starts the wait for a partner.
     *
     * <p>Silent when the species does not breed, when this one is still a juvenile, or when the
     * offering was an ordinary feed — the caller does not have to know which case it is in.
     */
    private void tryEnterLove(BondProfile profile, Item offered) {
        BondBreeding breeding = profile.breeding().orElse(null);
        if (breeding == null || breeding.item() != offered) {
            return;
        }
        if (bondState().isJuvenile() || bondState().breedCooldown() > 0
                || bondLevel() < breeding.minBond()) {
            return;
        }
        bondState().setLoveTicks(breeding.loveSeconds() * 20);
        PathfinderMob mob = bondMob();
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(),
                    mob.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * Pair off with a nearby partner that is also waiting, and produce one juvenile.
     *
     * <p>Both parents pay the cooldown and both drop out of the mood, so a trio cannot chain into a
     * second birth on the same tick. The search is deliberately narrow — same entity type, same
     * state, within the profile's range — because a herd is something a player builds up, not
     * something that happens by two strangers wandering past each other.
     */
    private void tickBreeding(BondProfile profile) {
        BondBreeding breeding = profile.breeding().orElse(null);
        if (breeding == null || bondState().loveTicks() <= 0) {
            return;
        }
        PathfinderMob mob = bondMob();
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        for (PathfinderMob candidate : level.getEntitiesOfClass(PathfinderMob.class,
                mob.getBoundingBox().inflate(breeding.partnerRange()),
                other -> other != mob && other.getType() == mob.getType() && other.isAlive())) {
            if (!(candidate instanceof BondableBeast partner) || partner.bondState().loveTicks() <= 0) {
                continue;
            }
            bondState().setLoveTicks(0);
            partner.bondState().setLoveTicks(0);
            bondState().setBreedCooldown(breeding.cooldownSeconds() * 20);
            partner.bondState().setBreedCooldown(breeding.cooldownSeconds() * 20);
            spawnJuvenile(level, profile, breeding, partner);
            return;
        }
    }

    /** Create the calf: same entity type, scaled down, and already part-way trusting. */
    private void spawnJuvenile(ServerLevel level, BondProfile profile, BondBreeding breeding,
                               BondableBeast partner) {
        PathfinderMob mob = bondMob();
        if (!(mob.getType().create(level, EntitySpawnReason.BREEDING) instanceof PathfinderMob child)
                || !(child instanceof BondableBeast baby)) {
            return;
        }
        // Offset slightly so the calf is not born inside its parent's box, matching how the
        // Duplication ability places a clone.
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        child.setPos(mob.getX() + Math.cos(angle) * 0.6, mob.getY(), mob.getZ() + Math.sin(angle) * 0.6);
        child.setYRot(mob.getYRot());

        baby.bondState().setOwner(bondState().ownerUUID() != null
                ? bondState().ownerUUID() : partner.bondState().ownerUUID());
        int inherited = (bondLevel() + partner.bondLevel()) / 2 * breeding.inheritedBondPercent() / 100;
        baby.setSyncedBondLevel(baby.bondState().setLevel(inherited, profile.maxBond()));
        baby.bondState().beginGrowth(breeding.growSeconds() * 20);
        baby.applyGrowthScale(breeding);

        level.addFreshEntity(child);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(),
                mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
    }

    /** Age a juvenile, and restore it to full size on the tick it finishes. */
    private void tickGrowth(BondProfile profile) {
        BondBreeding breeding = profile.breeding().orElse(null);
        if (breeding == null || !bondState().isJuvenile()) {
            return;
        }
        // tickBond only reaches here once a second, so a second of real time is 20 of the timer.
        for (int i = 0; i < 20; i++) {
            if (bondState().tickGrowth()) {
                applyGrowthScale(breeding);
                return;
            }
        }
    }

    /**
     * Push the juvenile/adult size onto {@code Attributes.SCALE}.
     *
     * <p>{@code SCALE} drives the hitbox as well as the model, so this is the whole of "it is small";
     * nothing else has to know, and no baby {@code EntityType} has to be registered. Null-tolerant
     * because the attribute is not guaranteed present on every entity's supplier, and a creature
     * that cannot be scaled should still be born.
     */
    default void applyGrowthScale(BondBreeding breeding) {
        AttributeInstance scale = bondMob().getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(bondState().isJuvenile() ? breeding.juvenileScale() : 1.0);
        }
    }

    // ── persistence ──────────────────────────────────────────────────────────

    /** Call from {@code addAdditionalSaveData}. */
    default void saveBond(ValueOutput output) {
        bondState().save(output);
    }

    /**
     * Call from {@code readAdditionalSaveData}.
     *
     * <p>Clamps against the ceiling the profile declares <em>now</em>, so lowering {@code maxBond} in
     * a datapack pulls existing creatures down to it instead of leaving them permanently above it.
     */
    default void loadBond(ValueInput input) {
        BondProfile profile = bondProfile();
        bondState().load(input, profile != null ? profile.maxBond() : Integer.MAX_VALUE);
        setSyncedBondLevel(bondState().level());
        // A juvenile's size lives on an attribute, not in its save data, so it has to be re-applied
        // on load or a calf comes back full-grown while its growth timer keeps running.
        if (profile != null) {
            profile.breeding().ifPresent(this::applyGrowthScale);
        }
    }

    // ── sound ────────────────────────────────────────────────────────────────

    /** Play a profile-declared sound at the creature, or nothing when the id is absent or unknown. */
    private void playBondSound(Optional<Identifier> id, float volume, float pitch) {
        id.flatMap(BondableBeast::resolveSound).ifPresent(sound ->
                bondMob().level().playSound(null, bondMob().blockPosition(), sound,
                        SoundSource.NEUTRAL, volume, pitch));
    }

    private static Optional<SoundEvent> resolveSound(Identifier id) {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceKey.create(Registries.SOUND_EVENT, id))
                .map(Holder::value);
    }
}
