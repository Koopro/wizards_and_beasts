package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerFleeWhenPouchStolen;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerFollowBondedPlayerGoal;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerSeekShinyBlockGoal;
import at.koopro.wizardsandbeasts.entity.niffler.ai.NifflerSeekShinyItemGoal;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.MagizoologyXPEvent;
import at.koopro.wizardsandbeasts.event.bestiary.niffler.NifflerPouchOpenEvent;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.network.bestiary.niffler.NifflerCarrySyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import at.koopro.wizardsandbeasts.util.MagizoologyHelper;
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

public class NifflerEntity extends GeoEntityBase {

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

    // ─── Bond milestone XP tags ───────────────────────────────────────────────
    private static final int[] BOND_XP_MILESTONES = {20, 50, 80, 100};

    // ─── Per-entity state (NBT-backed) ────────────────────────────────────────
    private final NifflerPouchInventory pouch = new NifflerPouchInventory(getPouchCapacity());
    @Nullable private UUID ownerUUID;
    private int bondLevel;
    private int feedCooldown;

    // ─── Transient ticks ──────────────────────────────────────────────────────
    private int ticksSincePouchAccess;
    private int proximityBondTicks;
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
        goalSelector.addGoal(5, new NifflerFollowBondedPlayerGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.4));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ─── Tick ─────────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (feedCooldown > 0) feedCooldown--;
        if (ticksSincePouchAccess > 0) ticksSincePouchAccess--;

        tickProximityBond();
        tickPeek();
        syncPouchFull();
    }

    private void tickProximityBond() {
        if (tickCount % 20 != 0) return;
        if (ownerUUID == null) return;
        Player owner = level().getPlayerByUUID(ownerUUID);
        if (owner == null || distanceToSqr(owner) > 36.0) {
            proximityBondTicks = 0;
            return;
        }
        proximityBondTicks++;
        if (proximityBondTicks >= 30) {
            proximityBondTicks = 0;
            increaseBond(owner, 1, false);
        }
    }

    private void tickPeek() {
        if (!isCarried() || bondLevel < 100) {
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

    public void storeItem(ItemStack stack) {
        if (!level().isClientSide()) {
            pouch.addItem(stack); // Assuming NifflerPouchInventory has addItem
            syncPouchFull();
        }
    }

    // ─── Interaction ──────────────────────────────────────────────────────────
    @Override
    protected @NonNull InteractionResult mobInteract(@NonNull Player player, @NonNull InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);

        // Feed interaction
        if (isFeedItem(held)) {
            return tryFeed(player, hand, held);
        }

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

    private @NonNull InteractionResult tryFeed(Player player, InteractionHand hand, ItemStack held) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (feedCooldown > 0) {
            level().playSound(null, blockPosition(), ModSounds.NIFFLER_HISS.get(), SoundSource.NEUTRAL, 0.7f, 1.2f);
            return InteractionResult.SUCCESS;
        }

        int gain = 0;
        int cooldownSecs = 0;
        if (held.is(Items.DIAMOND)) { gain = 20; cooldownSecs = 120; }
        else if (held.is(Items.GOLD_INGOT)) { gain = 15; cooldownSecs = 60; }
        else if (held.is(Items.GOLD_NUGGET)) { gain = 5; cooldownSecs = 30; }

        if (gain == 0) return InteractionResult.PASS;

        if (!player.isCreative()) held.shrink(1);
        feedCooldown = cooldownSecs * 20;
        increaseBond(player, gain, true);
        level().playSound(null, blockPosition(), ModSounds.NIFFLER_EAT.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private boolean isFeedItem(ItemStack stack) {
        return stack.is(Items.DIAMOND) || stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_NUGGET);
    }

    private @NonNull InteractionResult tryToggleCarry(Player player) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (bondLevel < 80) return InteractionResult.PASS;

        if (isBaby()) {
            level().playSound(null, blockPosition(), ModSounds.NIFFLER_SQUIRM.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        if (isCarried()) {
            setCarried(null);
        } else {
            if (ownerUUID == null) ownerUUID = player.getUUID();
            if (!player.getUUID().equals(ownerUUID)) return InteractionResult.PASS;
            setCarried(player);
        }
        return InteractionResult.SUCCESS;
    }

    private @NonNull InteractionResult tryOpenPouch(Player player) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        if (bondLevel < 50) {
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
                if (ownerUUID != null) {
                    Player owner = level().getPlayerByUUID(ownerUUID);
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
        if (!level().isClientSide() && isCarried() && ownerUUID != null) {
            Player carrier = level().getPlayerByUUID(ownerUUID);
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
    public void increaseBond(Player player, int amount, boolean fireXp) {
        if (ownerUUID == null) ownerUUID = player.getUUID();

        if (MagizoologyHelper.isMagizoologist(player)) {
            amount = (int) Math.ceil(amount * 1.5);
        }

        int old = bondLevel;
        bondLevel = Math.min(100, bondLevel + amount);
        entityData.set(DATA_BOND_LEVEL, bondLevel);

        level().playSound(null, blockPosition(), ModSounds.NIFFLER_HAPPY.get(), SoundSource.NEUTRAL, 0.6f, 1.0f);

        if (fireXp) {
            for (int milestone : BOND_XP_MILESTONES) {
                if (old < milestone && bondLevel >= milestone) {
                    NeoForge.EVENT_BUS.post(new MagizoologyXPEvent(player, milestone, "niffler_bond_" + milestone));
                    if (milestone == 80 && level() instanceof ServerLevel && player instanceof ServerPlayer sp) {
                        BestiaryDataHelper.setTier(sp, BESTIARY_ID, DiscoveryTier.MASTERED);
                    }
                }
            }
        }
    }

    private void decreaseBond(int amount) {
        bondLevel = Math.max(0, bondLevel - amount);
        entityData.set(DATA_BOND_LEVEL, bondLevel);
    }

    public boolean canAccessPouch(Player player) {
        int threshold = MagizoologyHelper.isMagizoologist(player) ? 30 : 50;
        return bondLevel >= threshold;
    }

    /** Called when player retrieves item from pouch and returns it (bond +10). */
    public void onItemReturnedToPouch(Player player) {
        increaseBond(player, 10, true);
    }

    // ─── Peek trigger ─────────────────────────────────────────────────────────
    /** Called when a shiny is detected nearby the carrier — forces immediate peek. */
    public void triggerPeek() {
        if (!level().isClientSide() && isCarried() && bondLevel >= 100 && peekPhase == 0) {
            nextPeekDelay = 0;
        }
    }

    private void resetPeekDelay() {
        nextPeekDelay = 200 + random.nextInt(201); // 200–400 ticks
    }

    // ─── Death + drops ────────────────────────────────────────────────────────
    @Override
    protected void dropCustomDeathLoot(@NonNull ServerLevel level, @NonNull DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        pouch.dropAll(this);
        if (ownerUUID != null) {
            Player owner = level.getPlayerByUUID(ownerUUID);
            if (owner != null) {
                owner.setData(ModAttachments.CARRIED_NIFFLER.get(), CarriedNifflerAttachment.EMPTY);
            }
        }
    }

    // ─── NBT ──────────────────────────────────────────────────────────────────
    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        pouch.save(output);
        if (ownerUUID != null) output.store("OwnerUUID", com.mojang.serialization.Codec.STRING, ownerUUID.toString());
        output.store("BondLevel", com.mojang.serialization.Codec.INT, bondLevel);
        output.store("FeedCooldown", com.mojang.serialization.Codec.INT, feedCooldown);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        pouch.load(input);
        ownerUUID = input.read("OwnerUUID", com.mojang.serialization.Codec.STRING)
                .map(s -> { try { return java.util.UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } })
                .orElse(null);
        bondLevel = Math.max(0, Math.min(100, input.read("BondLevel", com.mojang.serialization.Codec.INT).orElse(0)));
        feedCooldown = Math.max(0, input.read("FeedCooldown", com.mojang.serialization.Codec.INT).orElse(0));
        entityData.set(DATA_BOND_LEVEL, bondLevel);
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
    public @Nullable UUID getOwnerUUID() { return ownerUUID; }
    public int getBondLevel() { return bondLevel; }
    public boolean isCarried() { return entityData.get(DATA_IS_CARRIED); }
    public boolean isPouchFull() { return entityData.get(DATA_POUCH_FULL); }
    public int getDataPeekTick() { return entityData.get(DATA_PEEK_TICK); }
    public int getTicksSincePouchAccess() { return ticksSincePouchAccess; }
    protected int getPouchCapacity() { return 27; }
    public boolean isBaby() { return false; }

    /** Force-sets bond level (debug/command use only — bypasses cooldowns and XP events). */
    public void setForcedBond(int value) {
        bondLevel = Math.max(0, Math.min(100, value));
        entityData.set(DATA_BOND_LEVEL, bondLevel);
    }

    /** Transfers owner UUID and bond level from another Niffler (used on baby → adult growth). */
    public void transferBondFrom(NifflerEntity source) {
        this.ownerUUID = source.ownerUUID;
        this.bondLevel = source.bondLevel;
        entityData.set(DATA_BOND_LEVEL, bondLevel);
    }

    // ─── GeckoLib ─────────────────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("niffler", 5, IDLE_ANIM, WALK_ANIM));
    }
}
