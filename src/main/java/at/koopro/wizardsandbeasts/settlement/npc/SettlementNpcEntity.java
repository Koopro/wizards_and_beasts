package at.koopro.wizardsandbeasts.settlement.npc;

import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.RoleHost;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.settlement.SettlementNames;
import at.koopro.wizardsandbeasts.travel.courier.CourierService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * One of a settlement's people. A single entity for every profession, as {@code GoblinTellerEntity} is for its four
 * roles: what differs is {@code npc_roles/<id>.json} and a skin, and a role is not worth an {@code EntityType}.
 *
 * <p>Who they are is the post's record ({@code SettlementData}), not this object: this is one copy of them, which a
 * chunk can unload and a post can replace. They never drop anything and do not die: {@code SettlementEvents} has them
 * Apparate away from a lethal blow, and their post brings them back.
 *
 * <p>Implements {@link Npc}, so the Ministry counts them as a person. They are in neither the {@code muggles} tag nor
 * {@code ministry_officials}, so a wizard's cast in a hamlet is seen by no Muggle and reported by nobody.
 */
public class SettlementNpcEntity extends PathfinderMob implements Npc, RoleHost {

    /** How far from their post someone wanders. */
    public static final int HOME_RADIUS = 10;
    /** How long they stand and listen after being spoken to. */
    private static final int LISTEN_TICKS = 200;

    /** The role's id. Synced because the client picks the skin from it. */
    private static final EntityDataAccessor<String> DATA_ROLE =
            SynchedEntityData.defineId(SettlementNpcEntity.class, EntityDataSerializers.STRING);

    private long listeningUntil;

    public SettlementNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, "");
    }

    public String roleId() {
        return this.entityData.get(DATA_ROLE);
    }

    public void setRoleId(String id) {
        this.entityData.set(DATA_ROLE, id);
    }

    /** The role's definition, on the server; null when its file has gone from the datapack. */
    public @Nullable NpcRole role() {
        return NpcRoles.get(roleId());
    }

    @Override
    public String hostRole() {
        return roleId();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Role", roleId());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("Role").ifPresent(this::setRoleId);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ListenGoal());
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS_SERVER;
        }
        listeningUntil = level().getGameTime() + LISTEN_TICKS;
        getNavigation().stop();
        if (ModuleManager.isEnabled(Module.SETTLEMENTS)) {
            CourierService.deliverAt(serverPlayer, this);
            if (LocationProgression.openAtHost(serverPlayer, this)) {
                return InteractionResult.SUCCESS;
            }
        }
        // Their shop's module is off: they are still a person, and still say so.
        serverPlayer.sendSystemMessage(Component.translatable("npc.wizards_and_beasts.closed", getDisplayName(),
                SettlementNames.of(serverPlayer, this)));
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Stands still and faces whoever spoke, rather than strolling out of reach of their own counter. */
    private final class ListenGoal extends Goal {

        private ListenGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return level().getGameTime() < listeningUntil;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }
    }
}
