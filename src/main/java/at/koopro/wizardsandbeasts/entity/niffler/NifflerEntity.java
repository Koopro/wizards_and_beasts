package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerFleeWhenPouchStolen;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerHoardGoal;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;

/**
 * The Niffler: a small, harmless burrower with a pouch that holds far more than it should, and an eye for anything
 * that glitters.
 *
 * <p>What lives where. Treasure — what it wants and how much — is tags ({@link NifflerTreasure}). It finds treasure on
 * the ground ({@code NifflerSeekShinyItemGoal}) and in the ground ({@code NifflerSeekShinyBlockGoal}), keeps it in the
 * pouch ({@link NifflerPouchInventory}, saved on the entity, dropped when it dies), and a wild one takes its haul home
 * to sit over it ({@code NifflerHoardGoal}; home is vanilla's saved mob home, set where it first appeared). Bond,
 * feeding, following and the pouch window are the shared bond layer's ({@code creature_bonds/niffler.json}). Struck,
 * it bolts. It has a coat: classic black, and rarely brown, grey or pale.
 */
public class NifflerEntity extends GeoEntityBase implements BondableBeast,
        at.koopro.wizardsandbeasts.creature.variant.VariantHolder {

    /** Its coat, synced. The classic black is the base texture; the others are {@code textures/entity/niffler/<coat>.png}. */
    public enum Coat implements at.koopro.wizardsandbeasts.creature.variant.CreatureVariant {
        CLASSIC(null, 70), DARK_BROWN("niffler/dark_brown", 12), GREY("niffler/grey", 12), PALE("niffler/pale", 6);

        private static final Coat[] VALUES = values();
        private final @Nullable String texture;
        private final int weight;

        Coat(@Nullable String texture, int weight) {
            this.texture = texture;
            this.weight = weight;
        }

        public @Nullable String texture() {
            return texture;
        }

        @Override
        public @Nullable String variantTexture() {
            return texture;
        }

        @Override
        public int authoredWeight() {
            return weight;
        }

        public static Coat byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : CLASSIC;
        }

    }

    public static final int HOME_RADIUS = 16;
    private static final byte FLAG_DIGGING = 1;
    private static final byte FLAG_HOARDING = 2;
    private static final String ACTION_CONTROLLER = "niffler_action";
    private static final double RUN_SPEED_SQR = 0.09 * 0.09;


    // ─── Synched data ─────────────────────────────────────────────────────────
    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_CARRIED =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_POUCH_FULL =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_PEEK_TICK =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_COAT =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BYTE);

    // ─── Animations ───────────────────────────────────────────────────────────
    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("niffler", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("niffler", "walk");
    private static final RawAnimation RUN_ANIM = AnimHelper.loop("niffler", "run");
    private static final RawAnimation HOARD_ANIM = AnimHelper.loop("niffler", "hoard");

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
    private int digCooldown;

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
                .add(Attributes.FOLLOW_RANGE, 24.0)
                // Drawn big enough to carry a snout and a face, then scaled down to a small animal.
                .add(Attributes.SCALE, 0.7);
    }

    // ─── Entity data ──────────────────────────────────────────────────────────
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOND_LEVEL, 0);
        builder.define(DATA_IS_CARRIED, false);
        builder.define(DATA_POUCH_FULL, false);
        builder.define(DATA_PEEK_TICK, 0);
        builder.define(DATA_COAT, (byte) Coat.CLASSIC.ordinal());
        builder.define(DATA_ACTION, (byte) 0);
    }

    // ─── Goals ────────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        // Not a fighter: struck, it bolts.
        goalSelector.addGoal(2, new PanicGoal(this, 1.7));
        goalSelector.addGoal(2, new NifflerFleeWhenPouchStolen(this));
        goalSelector.addGoal(3, new NifflerSeekShinyItemGoal(this));
        goalSelector.addGoal(4, new NifflerSeekShinyBlockGoal(this));
        // A pocketed Niffler is inside the player; it must not also be pathing to them.
        goalSelector.addGoal(5, new FollowBondedOwnerGoal<>(this, n -> !n.isCarried()));
        goalSelector.addGoal(6, new NifflerHoardGoal(this));
        goalSelector.addGoal(7, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ─── Tick ─────────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (ticksSincePouchAccess > 0) ticksSincePouchAccess--;
        if (digCooldown > 0) digCooldown--;
        if (!hasHome() && getOwnerUUID() == null) {
            setHomeTo(blockPosition(), HOME_RADIUS);   // spawn eggs and old saves arrive without a burrow
        }

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

    // ─── Spawning ─────────────────────────────────────────────────────────────
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(@NonNull ServerLevelAccessor level, @NonNull DifficultyInstance difficulty,
                                                  @NonNull EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        // The authored 70/12/12/6 roll, under the server's creature rules.
        setCoat(at.koopro.wizardsandbeasts.creature.variant.CreatureVariants.roll("niffler", Coat.values(), getRandom()));
        setHomeTo(blockPosition(), HOME_RADIUS);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    // ── variant (Creature Lab) ──

    @Override
    public at.koopro.wizardsandbeasts.creature.variant.CreatureVariant variant() {
        return coat();
    }

    @Override
    public boolean applyVariant(String variantId) {
        for (Coat candidate : Coat.values()) {
            if (candidate.variantId().equals(variantId)) {
                setCoat(candidate);
                return true;
            }
        }
        return false;
    }

    public Coat coat() {
        return Coat.byId(entityData.get(DATA_COAT));
    }

    public void setCoat(Coat coat) {
        entityData.set(DATA_COAT, (byte) coat.ordinal());
    }

    // ─── Treasure ─────────────────────────────────────────────────────────────

    /**
     * Takes treasure off the ground into the pouch: as much as fits, the rest left lying where it was. Server-side.
     * The item flies to the Niffler (vanilla's pickup packet) and something gold makes it very happy.
     *
     * @return how many it took
     */
    public int pickUp(ItemEntity item) {
        if (level().isClientSide() || !item.isAlive()) return 0;
        ItemStack stack = item.getItem();
        int value = NifflerTreasure.value(stack);
        int before = stack.getCount();
        ItemStack rest = pouch.addItem(stack.copy());
        int taken = before - rest.getCount();
        if (taken <= 0) return 0;
        take(item, taken);
        if (rest.isEmpty()) {
            item.discard();
        } else {
            item.setItem(rest);
        }
        onPickedUpShinyItem();
        found(value);
        return taken;
    }

    /** The beat after a find: a little sparkle, a chirp, and a celebration if it was gold. */
    private void found(int value) {
        if (!(level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 0.4, getZ(), 3 + value, 0.2, 0.2, 0.2, 0.0);
        level.playSound(null, blockPosition(), ModSounds.NIFFLER_HAPPY.get(), SoundSource.NEUTRAL, 0.5f,
                1.0f + 0.1f * value);
        triggerAnim(ACTION_CONTROLLER, value >= NifflerTreasure.HIGH_VALUE ? "celebrate" : "pickup");
    }

    /** It has caught the scent of something: a sniff, and a snuffle now and then. */
    public void onSpottedTreasure() {
        if (level().isClientSide()) return;
        triggerAnim(ACTION_CONTROLLER, "sniff");
        if (getRandom().nextInt(3) == 0) {
            playSound(ModSounds.NIFFLER_AMBIENT.get(), 0.4f, 1.3f);
        }
    }

    /** How many treasures are in the pouch. */
    public int treasureCount() {
        int n = 0;
        for (int i = 0; i < pouch.getContainerSize(); i++) {
            n += pouch.getItem(i).getCount();
        }
        return n;
    }

    /** Sitting over the hoard: turns something over, pleased with itself. */
    public void admireHoard() {
        if (level().isClientSide()) return;
        triggerAnim(ACTION_CONTROLLER, getRandom().nextBoolean() ? "celebrate" : "pickup");
        playSound(ModSounds.NIFFLER_HAPPY.get(), 0.4f, 1.1f);
    }

    // ─── Digging ──────────────────────────────────────────────────────────────

    public int digCooldown() {
        return digCooldown;
    }

    public void startDigCooldown(int ticks) {
        digCooldown = Math.max(digCooldown, ticks);
    }

    /** Dirt flying: the dig clip, block-crack particles and the scrabbling sound. */
    public void digEffects(BlockPos pos, BlockState state) {
        if (!(level() instanceof ServerLevel level)) return;
        triggerAnim(ACTION_CONTROLLER, "dig");
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
        level.playSound(null, pos, ModSounds.NIFFLER_DIG.get(), SoundSource.NEUTRAL, 0.6f, 0.9f + getRandom().nextFloat() * 0.2f);
    }

    /**
     * Mines one block, for real: it is removed. Treasure (its drops) goes into the pouch, anything that does not fit is
     * left on the ground; loose earth dug through on the way drops as it would for anyone.
     */
    public void mine(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(NifflerTreasure.ORE)) {
            java.util.List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this, ItemStack.EMPTY);
            level.destroyBlock(pos, false, this);
            int best = 0;
            for (ItemStack drop : drops) {
                best = Math.max(best, NifflerTreasure.value(drop));
                ItemStack rest = pouch.addItem(drop);
                if (!rest.isEmpty()) {
                    Block.popResource(level, pos, rest);
                }
            }
            onPickedUpShinyItem();
            found(Math.max(best, NifflerTreasure.LOW));
        } else {
            level.destroyBlock(pos, true, this);
        }
    }

    // ─── Action flags (synced for animation) ──────────────────────────────────

    public boolean isDigging() {
        return (entityData.get(DATA_ACTION) & FLAG_DIGGING) != 0;
    }

    public boolean isHoarding() {
        return (entityData.get(DATA_ACTION) & FLAG_HOARDING) != 0;
    }

    public void setDigging(boolean on) {
        setAction(FLAG_DIGGING, on);
    }

    public void setHoarding(boolean on) {
        setAction(FLAG_HOARDING, on);
    }

    private void setAction(byte flag, boolean on) {
        byte flags = entityData.get(DATA_ACTION);
        byte next = (byte) (on ? flags | flag : flags & ~flag);
        if (next != flags) entityData.set(DATA_ACTION, next);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            triggerAnim(ACTION_CONTROLLER, "hit");
            setHoarding(false);
            setDigging(false);
        }
        return hurt;
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (!level().isClientSide()) {
            triggerAnim(ACTION_CONTROLLER, "death");
        }
        super.die(cause);
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
        output.putInt("Coat", coat().ordinal());
        output.putInt("DigCooldown", digCooldown);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        pouch.load(input);
        loadBond(input);
        setCoat(Coat.byId(input.getIntOr("Coat", 0)));
        digCooldown = input.getIntOr("DigCooldown", 0);
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
    /**
     * Movement (idle, walk, run, or sat over its hoard) first, one-shots after: GeckoLib applies controllers in order
     * and the last to touch a bone wins, so a dig or a celebration shows over the gait.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<NifflerEntity>("niffler_movement", 4, test -> {
            if (isHoarding()) return test.setAndContinue(HOARD_ANIM);
            if (!test.isMoving()) return test.setAndContinue(IDLE_ANIM);
            double dx = getX() - xo;
            double dz = getZ() - zo;
            return test.setAndContinue(dx * dx + dz * dz > RUN_SPEED_SQR ? RUN_ANIM : WALK_ANIM);
        }));
        controllers.add(new AnimationController<NifflerEntity>(ACTION_CONTROLLER, 0, test -> PlayState.STOP)
                .triggerableAnim("sniff", AnimHelper.playOnce("niffler", "sniff"))
                .triggerableAnim("dig", AnimHelper.playOnce("niffler", "dig"))
                .triggerableAnim("pickup", AnimHelper.playOnce("niffler", "pickup"))
                .triggerableAnim("celebrate", AnimHelper.playOnce("niffler", "celebrate"))
                .triggerableAnim("hit", AnimHelper.playOnce("niffler", "hit"))
                .triggerableAnim("death", AnimHelper.playOnce("niffler", "death")));
    }
}
