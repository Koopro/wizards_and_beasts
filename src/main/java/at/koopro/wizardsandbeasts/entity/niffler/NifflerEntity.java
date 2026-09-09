package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerFleeWhenPouchStolen;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerSeekShinyBlockGoal;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerSeekShinyItemGoal;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.NifflerPouchOpenEvent;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.network.bestiary.niffler.NifflerCarrySyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.UUID;

public class NifflerEntity extends GeoEntityBase implements BondableBeast {

    // ─── Synched data ─────────────────────────────────────────────────────────
    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_CARRIED =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_POUCH_FULL =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PEEK_TICK =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.INT);

    // ─── Animations ───────────────────────────────────────────────────────────
    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("niffler", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("niffler", "walk");

    // ─── Bestiary ID ──────────────────────────────────────────────────────────
    public static final Identifier BESTIARY_ID = Identifier.fromNamespaceAndPath("wizards_and_beasts", "niffler");

    /**
     * Ceiling used by the two paths that set a bond without going through the profile: the debug
     * command and the baby → adult hand-over. Both predate the datapack layer and neither has a
     * player to attribute the change to, so neither can reasonably fail because a profile is
     * missing. Kept equal to {@code creature_bonds/niffler.json}'s {@code maxBond}, which
     * {@code CreatureBondProfileTest} asserts.
     */
    public static final int MAX_BOND = 100;

    // ─── Per-entity state (NBT-backed) ────────────────────────────────────────
    private final NifflerPouchInventory pouch = new NifflerPouchInventory(getPouchCapacity());

    /**
     * Owner, bond level and the feed cooldown, on the shared {@code creature.bond} storage.
     *
     * <p>These were three fields on this class, and the rules that moved them were three more
     * methods. They now live in {@link BondState} and are driven by
     * {@code data/wizards_and_beasts/creature_bonds/niffler.json}, which restates this creature's
     * original numbers exactly: diamond 20 / gold ingot 15 / nugget 5, milestones at 20/50/80/100,
     * follow from 50, MASTERED at 80. The save keys are unchanged, so existing Nifflers keep their
     * owner and their bond.
     */
    private final BondState bond = new BondState();

    // ─── Transient ticks ──────────────────────────────────────────────────────
    private int ticksSincePouchAccess;
    private int peekPhase; // 0=idle, 1=rising, 2=held, 3=falling
    private int peekPhaseTick;
    private int nextPeekDelay;

    public NifflerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        resetPeekDelay();
    }

    // ─── Attributes ───────────────────────────────────────────────────────────
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    // ─── Entity data ──────────────────────────────────────────────────────────
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOND_LEVEL, 0);
        builder.define(DATA_IS_CARRIED, false);
        builder.define(DATA_POUCH_FULL, false);
        builder.define(DATA_PEEK_TICK, 0);
    }

    // ─── Goals ────────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new NifflerFleeWhenPouchStolen(this));
        goalSelector.addGoal(3, new NifflerSeekShinyItemGoal(this));
        goalSelector.addGoal(4, new NifflerSeekShinyBlockGoal(this));
        // A pocketed Niffler is inside the player; it must not also be pathing to them.
        goalSelector.addGoal(5, new FollowBondedOwnerGoal<>(this, n -> !n.isCarried()));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ─── Tick ─────────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (ticksSincePouchAccess > 0) ticksSincePouchAccess--;

        tickBond();
        tickPeek();
        syncPouchFull();
    }

    private void tickPeek() {
        if (!isCarried() || getBondLevel() < 100) {
            if (entityData.get(DATA_PEEK_TICK) != 0) entityData.set(DATA_PEEK_TICK, 0);
            return;
        }

        switch (peekPhase) {
            case 0 -> { // waiting
                nextPeekDelay--;
                if (nextPeekDelay <= 0) {
                    peekPhase = 1;
                    peekPhaseTick = 0;
                }
            }
            case 1 -> { // rising
                peekPhaseTick++;
                entityData.set(DATA_PEEK_TICK, Math.min(peekPhaseTick, 20));
                if (peekPhaseTick >= 20) {
                    peekPhase = 2;
                    peekPhaseTick = 0;
                }
            }
            case 2 -> { // held
                peekPhaseTick++;
                if (peekPhaseTick >= 40) {
                    peekPhase = 3;
                    peekPhaseTick = 0;
                }
            }
            case 3 -> { // falling
                peekPhaseTick++;
                entityData.set(DATA_PEEK_TICK, Math.max(0, 20 - peekPhaseTick));
                if (peekPhaseTick >= 20) {
                    peekPhase = 0;
                    entityData.set(DATA_PEEK_TICK, 0);
                    resetPeekDelay();
                }
            }
        }
    }

    private void syncPouchFull() {
        boolean full = pouch.isFull();
        if (entityData.get(DATA_POUCH_FULL) != full) {
            entityData.set(DATA_POUCH_FULL, full);
        }
    }

    /** Called by NifflerSeekShinyItemGoal when an item is picked up. */
    public void onPickedUpShinyItem() {
        if (!level().isClientSide()) {
            entityData.set(DATA_POUCH_FULL, pouch.isFull());
        }
    }

    // ─── Interaction ──────────────────────────────────────────────────────────
    @Override
    protected @NonNull InteractionResult mobInteract(@NonNull Player player, @NonNull InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) return InteractionResult.PASS;

        // Feeding, the feed cooldown and the bond gain are all the shared layer's now; it returns
        // PASS for anything this species does not eat, so the two hand-empty interactions below
        // still see every other item.
        InteractionResult fed = offerBondFood(player, hand);
        if (fed != InteractionResult.PASS) {
            return fed;
        }

        ItemStack held = player.getItemInHand(hand);

        // Pocket carry — sneak + empty hand
        if (held.isEmpty() && player.isShiftKeyDown()) {
            return tryToggleCarry(player);
        }

        // Pouch access
        if (held.isEmpty() && !player.isShiftKeyDown()) {
            return tryOpenPouch(player);
        }

        return super.mobInteract(player, hand);
    }

    private @NonNull InteractionResult tryToggleCarry(Player player) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (getBondLevel() < 80) return InteractionResult.PASS;

        if (isBaby()) {
            level().playSound(null, blockPosition(), ModSounds.NIFFLER_SQUIRM.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        if (isCarried()) {
            setCarried(null);
        } else {
            if (bond.ownerUUID() == null) bond.setOwner(player.getUUID());
            if (!bond.isOwnedBy(player.getUUID())) return InteractionResult.PASS;
            setCarried(player);
        }
        return InteractionResult.SUCCESS;
    }

    private @NonNull InteractionResult tryOpenPouch(Player player) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        if (getBondLevel() < 50) {
            level().playSound(null, blockPosition(), ModSounds.NIFFLER_HISS.get(), SoundSource.NEUTRAL, 1.0f, 0.8f);
            player.hurt(level().damageSources().generic(), 1.0f);
            ticksSincePouchAccess = 60;
            decreaseBond(10);
            return InteractionResult.SUCCESS;
        }

        NeoForge.EVENT_BUS.post(new NifflerPouchOpenEvent(this, player));
        ticksSincePouchAccess = 60;

        String title = hasCustomName() ? getCustomName().getString() + "'s Pouch" : "Niffler's Pouch";
        final int eid = this.getId();
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new NifflerPouchMenu(id, inv, this),
                net.minecraft.network.chat.Component.literal(title)),
                buf -> buf.writeInt(eid));
        return InteractionResult.SUCCESS;
    }

    // ─── Pocket carry system ──────────────────────────────────────────────────
    public void setCarried(@Nullable Player player) {
        if (!level().isClientSide()) {
            if (player != null) {
                setInvisible(true);
                entityData.set(DATA_IS_CARRIED, true);
                player.setData(ModAttachments.CARRIED_NIFFLER.get(),
                        new CarriedNifflerAttachment(this.getUUID()));
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    NifflerCarrySyncS2CPayload.sendToPlayer(sp);
                }
            } else {
                setInvisible(false);
                entityData.set(DATA_IS_CARRIED, false);
                // Clear attachment from owner
                {
                    Player owner = resolveBondOwner();
                    if (owner != null) {
                        owner.setData(ModAttachments.CARRIED_NIFFLER.get(), CarriedNifflerAttachment.EMPTY);
                        setPos(owner.getX(), owner.getY(), owner.getZ());
                        if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                            NifflerCarrySyncS2CPayload.sendToPlayer(sp);
                        }
                    }
                }
            }
        }
    }

    /** Called server-side each tick to follow the carrier. */
    @Override
    public void aiStep() {
        if (!level().isClientSide() && isCarried() && bond.ownerUUID() != null) {
            Player carrier = resolveBondOwner();
            if (carrier != null) {
                setPos(carrier.getX(), carrier.getY(), carrier.getZ());
            } else {
                // Carrier gone — drop
                setCarried(null);
            }
        }
        super.aiStep();
    }

    // ─── Bond system ──────────────────────────────────────────────────────────

    @Override
    public @NonNull BondState bondState() {
        return bond;
    }

    @Override
    public void setSyncedBondLevel(int level) {
        entityData.set(DATA_BOND_LEVEL, level);
    }

    @Override
    public int getSyncedBondLevel() {
        return entityData.get(DATA_BOND_LEVEL);
    }

    /**
     * The Niffler chirps on every bond gain, including the slow drip of company — the shared layer
     * has no opinion about noise, and this one is the creature's character.
     *
     * <p>Played before delegating so it still sounds at a full bond, which is what it did when the
     * whole method lived here.
     */
    @Override
    public void increaseBond(@NonNull Player player, int amount, boolean fireXp) {
        if (!level().isClientSide()) {
            level().playSound(null, blockPosition(), ModSounds.NIFFLER_HAPPY.get(), SoundSource.NEUTRAL, 0.6f, 1.0f);
        }
        BondableBeast.super.increaseBond(player, amount, fireXp);
    }

    private void resetPeekDelay() {
        nextPeekDelay = 200 + random.nextInt(201); // 200–400 ticks
    }

    // ─── Death + drops ────────────────────────────────────────────────────────
    @Override
    protected void dropCustomDeathLoot(@NonNull ServerLevel level, @NonNull DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        pouch.dropAll(this);
        Player owner = resolveBondOwner();
        if (owner != null) {
            owner.setData(ModAttachments.CARRIED_NIFFLER.get(), CarriedNifflerAttachment.EMPTY);
        }
    }

    // ─── NBT ──────────────────────────────────────────────────────────────────
    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        pouch.save(output);
        saveBond(output);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        pouch.load(input);
        loadBond(input);
    }

    // ─── Sound overrides ──────────────────────────────────────────────────────
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return ModSounds.NIFFLER_AMBIENT.get();
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(@NonNull DamageSource source) {
        return ModSounds.NIFFLER_HURT.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ModSounds.NIFFLER_DEATH.get();
    }

    // ─── Accessors ────────────────────────────────────────────────────────────
    public @NonNull NifflerPouchInventory getPouch() { return pouch; }
    public @Nullable UUID getOwnerUUID() { return bond.ownerUUID(); }

    /**
     * The bond level, read from synched data rather than the server-only field it used to read.
     *
     * <p>That field was never written on the client, so every client-side caller saw 0 no matter
     * how bonded the creature was — which is why {@code NifflerPocketLayer}'s {@code >= 100} gate
     * could never open and the pocket peek never drew. The value has been synced since the entity
     * was written; nothing was reading it.
     */
    public int getBondLevel() { return getSyncedBondLevel(); }
    public boolean isCarried() { return entityData.get(DATA_IS_CARRIED); }
    public boolean isPouchFull() { return entityData.get(DATA_POUCH_FULL); }
    public int getDataPeekTick() { return entityData.get(DATA_PEEK_TICK); }
    public int getTicksSincePouchAccess() { return ticksSincePouchAccess; }
    protected int getPouchCapacity() { return 27; }
    public boolean isBaby() { return false; }

    /** Force-sets bond level (debug/command use only — bypasses cooldowns and XP events). */
    public void setForcedBond(int value) {
        setSyncedBondLevel(bond.setLevel(value, MAX_BOND));
    }

    /** Transfers owner UUID and bond level from another Niffler (used on baby → adult growth). */
    public void transferBondFrom(NifflerEntity source) {
        bond.setOwner(source.bond.ownerUUID());
        setSyncedBondLevel(bond.setLevel(source.bond.level(), MAX_BOND));
    }

    // ─── GeckoLib ─────────────────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("niffler", 5, IDLE_ANIM, WALK_ANIM));
    }
}
