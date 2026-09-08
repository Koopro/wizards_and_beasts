package at.koopro.wizardsandbeasts.entity.dummy;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.dummy.DamageNumberS2CPayload;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModEntities;

/**
 * A duelling dummy: a target that reports what you did to it and refuses to die of it.
 *
 * <h2>Why a player-shaped {@link Mob} and not a block or an armour stand</h2>
 *
 * <p>The point of the thing is to answer "what does this spell actually do to a wizard", and the
 * honest answer depends on what the wizard is wearing. A {@code Mob} with the six ordinary
 * equipment slots gets that for free: a robe hung on the dummy contributes its armour and toughness
 * to the vanilla damage pipeline, so the number the dummy prints is the number a wizard in that
 * robe would take. A block would have had to reimplement armour, and an armour stand cannot be hurt
 * in a way that runs the pipeline at all.
 *
 * <p>Everything else follows from "never lies about damage": i-frames are cleared before each blow
 * so a fast wand is not silently measured at half its rate, and the figure recorded is the damage
 * <em>applied</em> after armour and absorption, not the amount the spell nominally rolled.
 *
 * <h2>It cannot be killed</h2>
 *
 * <p>Reaching zero health resets the dummy instead of killing it. Death would end the bout in the
 * middle of a rotation and drop the equipment being tested. The one exception is damage tagged
 * {@code bypasses_invulnerability} - {@code /kill} must still work, or a mis-placed dummy would be
 * unremovable by an operator.
 */
@NullMarked
public class DuellingDummyEntity extends Mob {

    /** Boss mode: raised by a banner, and worth its own bar. */
    private static final EntityDataAccessor<Boolean> BOSS =
            SynchedEntityData.defineId(DuellingDummyEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * Damage of the most recent blow, for the client's wobble.
     *
     * <p>Synced once per hit and decayed client-side rather than pushed down every tick: the shape
     * of the swing is a pure function of "how hard, how long ago", and both are already on the
     * client - {@code hurtTime} counts the ticks for free.
     */
    private static final EntityDataAccessor<Float> LAST_HIT =
            SynchedEntityData.defineId(DuellingDummyEntity.class, EntityDataSerializers.FLOAT);

    /** How often the scarecrow and decoy sweeps run. Once a second is plenty for either. */
    private static final int SWEEP_INTERVAL = 20;
    /** Anything under this reads as a rounding artefact rather than a hit worth a number. */
    private static final float MIN_REPORTED = 0.05f;
    /** How far a damage number carries. Beyond this the text is too small to read anyway. */
    private static final double NUMBER_RANGE = 48.0;

    private final DummyCombatLog log = new DummyCombatLog();

    private @Nullable DummyBossBar bossBar;

    /**
     * The attacker's weapon and its durability before the current blow, so
     * {@code dummyProtectEquipment} can put it back.
     *
     * <p>Restored a tick later rather than in place: vanilla damages the weapon in
     * {@code Player.attack} <em>after</em> the hurt call returns, so anything written during the
     * blow is overwritten a few lines later. There is no cancellable hook on
     * {@code ItemStack.postHurtEnemy} to use instead.
     */
    private @Nullable ItemStack protectedStack;
    private int protectedDamage;

    public DuellingDummyEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.applyConfiguredAttributes();
        this.setHealth(this.getMaxHealth());
    }

    public DuellingDummyEntity(Level level, double x, double y, double z) {
        this(ModEntities.DUELLING_DUMMY.get(), level);
        this.setPos(x, y, z);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS, false);
        builder.define(LAST_HIT, 0.0f);
    }

    @Override
    protected void registerGoals() {
        // Deliberately none. A dummy that walks off is not a dummy.
    }

    // --- configuration ------------------------------------------------------------

    /**
     * Pulls health and bare armour from config.
     *
     * <p>Called on spawn, on load and when boss mode changes, rather than every tick: these are
     * base values, and rewriting an attribute the equipment pipeline also reads is not something
     * to do twenty times a second.
     */
    private void applyConfiguredAttributes() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(this.isBoss() ? Config.dummyBossHealth : Config.dummyHealth);
        }
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Config.dummyArmor);
        }
    }

    public boolean isBoss() {
        return this.entityData.get(BOSS);
    }

    private void setBoss(boolean boss) {
        this.entityData.set(BOSS, boss);
        this.applyConfiguredAttributes();
        this.setHealth(this.getMaxHealth());
        if (!boss) {
            this.clearBossBar();
        }
    }

    /** Damage of the most recent blow. Read on the client to drive the wobble. */
    public float lastHitDamage() {
        return this.entityData.get(LAST_HIT);
    }

    public DummyMode mode() {
        return DummyMode.of(this.getItemBySlot(EquipmentSlot.HEAD));
    }

    // --- immobility ---------------------------------------------------------------

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity other) {
        // A dummy is furniture. Walking into one moves you, not it.
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // KNOCKBACK_RESISTANCE covers attacks; this covers explosions and everything else.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // --- damage -------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        // Every blow must count. Vanilla's ten ticks of invulnerability would swallow the second
        // half of a fast rotation and report a rate that is quietly, confidently wrong.
        this.invulnerableTime = 0;

        float before = this.getHealth();
        this.rememberEquipmentDurability(source);
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt) {
            this.protectedStack = null;
            return false;
        }
        float applied = before - this.getHealth();
        if (applied > MIN_REPORTED) {
            this.onDamaged(level, source, applied);
        }
        if (this.getHealth() <= 0.5f) {
            this.resetHealth(level);
        }
        return true;
    }

    private void onDamaged(ServerLevel level, DamageSource source, float applied) {
        this.entityData.set(LAST_HIT, applied);
        this.playSound(SoundEvents.WOOL_HIT, 0.7f, 0.9f + this.random.nextFloat() * 0.2f);

        this.broadcastNumber(level, applied, DummyDamageColours.of(source), source.getEntity());
        if (this.bossBar != null) {
            this.bossBar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));
        }

        if (source.getEntity() instanceof ServerPlayer attacker) {
            DummyCombatLog.Bout bout = this.log.hit(attacker.getUUID(), level.getGameTime(), applied);
            if (Config.dummyDpsMode == Config.DummyDpsMode.DYNAMIC) {
                PlayerFeedback.actionBar(attacker, describe(bout, false));
            }
            this.awardTrainingXp(level, applied);
        }
    }

    /**
     * Sends the floating number to whoever {@code dummyDamageNumbers} says should see it.
     *
     * <p>A packet rather than a particle because this mod draws in-world text by projecting it onto
     * the HUD - see {@code WorldDebugPanel} for why - and a HUD layer needs the number, not a
     * particle carrying it.
     *
     * @param amount positive for damage, negative for healing
     */
    private void broadcastNumber(ServerLevel level, float amount, int colour, @Nullable Entity attacker) {
        if (Config.dummyDamageNumbers == Config.DummyNumberMode.NONE) {
            return;
        }
        DamageNumberS2CPayload payload = new DamageNumberS2CPayload(
                this.getX(),
                this.getY() + this.getBbHeight() * 0.75,
                this.getZ(),
                amount,
                colour);
        if (Config.dummyDamageNumbers == Config.DummyNumberMode.SELF) {
            if (attacker instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, payload);
            }
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= NUMBER_RANGE * NUMBER_RANGE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private void awardTrainingXp(ServerLevel level, float applied) {
        if (Config.dummyXpPerDamage <= 0.0) {
            return;
        }
        int orbs = (int) Math.floor(applied * Config.dummyXpPerDamage);
        if (orbs > 0) {
            ExperienceOrb.award(level, this.position(), orbs);
        }
    }

    private void rememberEquipmentDurability(DamageSource source) {
        this.protectedStack = null;
        if (!Config.dummyProtectEquipment || !(source.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isEmpty() && weapon.isDamageableItem()) {
            this.protectedStack = weapon;
            this.protectedDamage = weapon.getDamageValue();
        }
    }

    private void restoreEquipmentDurability() {
        ItemStack weapon = this.protectedStack;
        this.protectedStack = null;
        if (weapon != null && weapon.isDamageableItem() && weapon.getDamageValue() > this.protectedDamage) {
            weapon.setDamageValue(this.protectedDamage);
        }
    }

    /**
     * Ambient damage is ignored; anything with a source behind it is not.
     *
     * <p>A dummy left in the rain that slowly drowned itself down to a reset would read as broken,
     * and the reset would land in the middle of somebody else's bout.
     */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (source.getEntity() == null && source.getDirectEntity() == null
                && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public void heal(float amount) {
        float before = this.getHealth();
        super.heal(amount);
        float healed = this.getHealth() - before;
        if (healed > MIN_REPORTED && this.level() instanceof ServerLevel level) {
            this.broadcastNumber(level, -healed, DummyDamageColours.healing(), null);
        }
    }

    private void resetHealth(ServerLevel level) {
        this.setHealth(this.getMaxHealth());
        this.entityData.set(LAST_HIT, 0.0f);
        level.playSound(null, this.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
                SoundSource.NEUTRAL, 0.6f, 1.4f);
        if (this.bossBar != null) {
            this.bossBar.setProgress(1.0f);
        }
    }

    // --- ticking ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.protectedStack != null) {
            this.restoreEquipmentDurability();
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // Belt and braces: nothing should be able to leave the dummy at zero, but a dead dummy is a
        // lost bout and lost equipment, so the invariant is re-asserted rather than assumed.
        if (this.getHealth() <= 0.0f) {
            this.resetHealth(level);
        }
        this.tickBossBar(level);
        this.tickBouts(level);
        if (this.tickCount % SWEEP_INTERVAL == 0) {
            this.tickMode(level);
        }
    }

    private void tickBossBar(ServerLevel level) {
        if (!this.isBoss()) {
            return;
        }
        if (this.bossBar == null) {
            this.bossBar = new DummyBossBar(this.getDisplayName());
        }
        this.bossBar.update(level, this);
    }

    /** Closes windows that have gone quiet, prints the summary and heals the dummy back up. */
    private void tickBouts(ServerLevel level) {
        if (this.log.isEmpty()) {
            return;
        }
        List<UUID> finished = this.log.expired(level.getGameTime(), Config.dummyBoutTimeoutTicks);
        for (UUID id : finished) {
            DummyCombatLog.Bout bout = this.log.end(id);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && Config.dummyDpsMode != Config.DummyDpsMode.OFF) {
                PlayerFeedback.actionBar(player, describe(bout, true));
            }
        }
        if (!finished.isEmpty() && this.log.isEmpty()) {
            this.resetHealth(level);
        }
    }

    private void tickMode(ServerLevel level) {
        switch (this.mode()) {
            case SCARECROW -> this.scareAnimals(level);
            case DECOY -> this.attractMonsters(level);
            case TRAINING -> { }
        }
    }

    /**
     * Nudges nearby animals away.
     *
     * <p>Pushes their navigation rather than installing an avoid goal on them: goals belong to the
     * mob that owns them, and a dummy that rewrote the goal set of every cow that wandered past
     * would leave those edits behind when it was dismantled.
     */
    private void scareAnimals(ServerLevel level) {
        if (!Config.dummyScarecrow) {
            return;
        }
        for (Animal animal : level.getEntitiesOfClass(Animal.class, this.searchBox(Config.dummyScareRadius))) {
            Vec3 away = animal.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 target = animal.position().add(away.normalize().scale(4.0));
            animal.getNavigation().moveTo(target.x, target.y, target.z, 1.25);
        }
    }

    private void attractMonsters(ServerLevel level) {
        if (!Config.dummyDecoy) {
            return;
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, this.searchBox(Config.dummyScareRadius))) {
            if (mob instanceof Enemy && mob.getTarget() == null && mob.canAttack(this)) {
                mob.setTarget(this);
            }
        }
    }

    private AABB searchBox(double radius) {
        return this.getBoundingBox().inflate(radius, Math.min(radius, 8.0), radius);
    }

    /** True while this dummy is suppressing hostile spawns at {@code pos}. */
    public boolean suppressesSpawnsAt(Vec3 pos) {
        return Config.dummyScarecrow
                && this.mode() == DummyMode.SCARECROW
                && this.position().closerThan(pos, Config.dummyScareRadius);
    }

    // --- interaction --------------------------------------------------------------

    @Override
    public InteractionResult interactAt(Player player, Vec3 hitVector, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.NAME_TAG)) {
            return InteractionResult.PASS;
        }
        if (player.isSpectator()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        // Crouch with an empty hand takes the whole dummy back, equipment and all. It is the only
        // way to pick one up: a dummy that could be punched down would not survive being used.
        if (held.isEmpty() && player.isShiftKeyDown()) {
            this.dismantle(level, !player.getAbilities().instabuild);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.is(ItemTags.BANNERS)) {
            this.setBoss(!this.isBoss());
            PlayerFeedback.actionBar(player, Component.translatable(this.isBoss()
                    ? "message.wizards_and_beasts.dummy.boss_on"
                    : "message.wizards_and_beasts.dummy.boss_off"));
            return InteractionResult.SUCCESS_SERVER;
        }
        EquipmentSlot slot = held.isEmpty()
                ? this.clickedSlot(hitVector)
                : this.getEquipmentSlotForItem(held);
        return this.swap(player, hand, slot) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
    }

    /**
     * Which slot a bare-handed click at this height is reaching for.
     *
     * <p>Bands mirror the armour stand's, which are tuned for exactly this body: they overlap on
     * purpose, and the first one holding an item wins, so a click at chest height on a dummy
     * wearing only boots still takes the boots rather than doing nothing.
     */
    private EquipmentSlot clickedSlot(Vec3 hitVector) {
        double y = hitVector.y / this.getScale();
        if (y >= 1.5 && this.hasItemInSlot(EquipmentSlot.HEAD)) {
            return EquipmentSlot.HEAD;
        }
        if (y >= 0.9 && y < 1.6 && this.hasItemInSlot(EquipmentSlot.CHEST)) {
            return EquipmentSlot.CHEST;
        }
        if (y >= 0.4 && y < 1.2 && this.hasItemInSlot(EquipmentSlot.LEGS)) {
            return EquipmentSlot.LEGS;
        }
        if (y < 0.55 && this.hasItemInSlot(EquipmentSlot.FEET)) {
            return EquipmentSlot.FEET;
        }
        return this.hasItemInSlot(EquipmentSlot.MAINHAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private boolean swap(Player player, InteractionHand hand, EquipmentSlot slot) {
        if (slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        ItemStack worn = this.getItemBySlot(slot);
        if (held.isEmpty() && worn.isEmpty()) {
            return false;
        }
        this.setItemSlot(slot, held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1));
        if (!held.isEmpty()) {
            held.shrink(1);
        }
        if (!worn.isEmpty() && !player.getInventory().add(worn)) {
            player.drop(worn, false);
        }
        this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.7f, 1.0f);
        return true;
    }

    /** Takes the dummy out of the world, returning it and anything on it. */
    public void dismantle(ServerLevel level, boolean drops) {
        if (drops) {
            ItemStack item = new ItemStack(MiscItemRegistry.DUELLING_DUMMY.get());
            if (this.hasCustomName()) {
                item.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            }
            this.spawnAtLocation(level, item);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack worn = this.getItemBySlot(slot);
                if (!worn.isEmpty()) {
                    this.spawnAtLocation(level, worn.copy());
                    this.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        level.playSound(null, this.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        this.discard();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.clearBossBar();
        super.remove(reason);
    }

    private void clearBossBar() {
        if (this.bossBar != null) {
            this.bossBar.removeAllPlayers();
            this.bossBar = null;
        }
    }

    // --- readout ------------------------------------------------------------------

    private static Component describe(DummyCombatLog.Bout bout, boolean summary) {
        String damage = format(bout.damage());
        String dps = format(bout.dps());
        if (bout.healing() > MIN_REPORTED) {
            return Component.translatable("message.wizards_and_beasts.dummy.readout_healing",
                    damage, dps, format(bout.healing()), format(bout.hps()));
        }
        return Component.translatable(summary
                        ? "message.wizards_and_beasts.dummy.readout_final"
                        : "message.wizards_and_beasts.dummy.readout",
                damage, dps, bout.hits(), format(bout.biggestHit()));
    }

    /** Formats a damage figure in whichever unit {@code dummyShowHearts} asked for. */
    private static String format(float value) {
        float shown = Config.dummyShowHearts ? value / 2.0f : value;
        return String.format(Locale.ROOT, "%.1f", shown);
    }

    // --- persistence --------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Boss", Codec.BOOL, this.isBoss());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("Boss", Codec.BOOL).ifPresent(boss -> this.entityData.set(BOSS, boss));
        this.applyConfiguredAttributes();
    }

    /**
     * The dummy's boss bar.
     *
     * <p>Its own small class so that deciding who is close enough to see the bar does not become a
     * third responsibility of {@link #tick()}.
     */
    private static final class DummyBossBar {
        private static final double VIEW_RANGE = 32.0;

        private final ServerBossEvent event;

        private DummyBossBar(Component name) {
            this.event = new ServerBossEvent(
                    name, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        }

        void setProgress(float progress) {
            this.event.setProgress(progress);
        }

        void update(ServerLevel level, LivingEntity owner) {
            this.event.setName(owner.getDisplayName());
            this.event.setProgress(Mth.clamp(owner.getHealth() / owner.getMaxHealth(), 0.0f, 1.0f));
            for (ServerPlayer player : level.players()) {
                boolean near = player.distanceToSqr(owner) <= VIEW_RANGE * VIEW_RANGE;
                boolean listed = this.event.getPlayers().contains(player);
                if (near && !listed) {
                    this.event.addPlayer(player);
                } else if (!near && listed) {
                    this.event.removePlayer(player);
                }
            }
        }

        void removeAllPlayers() {
            this.event.removeAllPlayers();
        }
    }
}
