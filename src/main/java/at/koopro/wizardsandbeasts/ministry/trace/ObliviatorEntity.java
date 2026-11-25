package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Obliviator who tidies up after a breach of the Statute of Secrecy (systems audit C2, 2026-11-25).
 *
 * <p>Canon: the Accidental Magic Reversal Squad and its Obliviators (PoA ch.3, Fudge on Aunt Marge: "Two members of
 * the Accidental Magic Reversal Squad were dispatched a few hours ago"; GoF ch.7, the Muggle campsite manager
 * Obliviated again and again). The Muggles forget; the Ministry does not.
 *
 * <p>She Apparates in when the Muggle report reaches the Ministry ({@link TraceRules#MUGGLE_REPORT_DELAY_TICKS}),
 * Obliviates every witness the incident recorded, says her piece to the caster if they are still about, and
 * Disapparates. Mechanically the witnesses' memory is the incident's witness list: once it is wiped an Auror can no
 * longer interview those Muggles about it. The incident and its charge stay on file. She cannot be harmed: struck,
 * she Disapparates on the spot. Transient: never saved, so a reload never strands her.
 */
@NullMarked
public class ObliviatorEntity extends PathfinderMob implements Npc {

    /** A beat after the crack, then the charm. */
    static final int OBLIVIATE_AT = 20;
    /** Long enough to be seen and heard, then gone. */
    static final int LEAVE_AT = 80;
    private static final double SPEAKING_RANGE = 32.0;

    private long incidentId = -1;
    private @Nullable UUID casterId;
    private List<UUID> witnesses = List.of();
    private int age;

    public ObliviatorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    /** Sends one Obliviator to {@code at} for {@code incident}. */
    static ObliviatorEntity apparate(ServerLevel level, Vec3 at, Incident incident, List<UUID> witnesses) {
        ObliviatorEntity obliviator = ModEntities.OBLIVIATOR.get().create(level, EntitySpawnReason.EVENT);
        if (obliviator == null) {
            throw new IllegalStateException("no obliviator entity type");
        }
        obliviator.incidentId = incident.id();
        obliviator.casterId = incident.caster();
        obliviator.witnesses = List.copyOf(witnesses);
        obliviator.setCustomName(Component.translatable("entity.wizards_and_beasts.obliviator.title"));
        obliviator.snapTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0f, 0.0f);
        level.addFreshEntity(obliviator);
        crack(level, obliviator.position());
        return obliviator;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 12.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        age++;
        if (age == OBLIVIATE_AT) {
            obliviate(level);
        } else if (age >= LEAVE_AT || incidentId < 0) {
            disapparate(level);
        }
    }

    private void obliviate(ServerLevel level) {
        for (UUID id : witnesses) {
            Entity witness = level.getEntity(id);
            if (witness instanceof LivingEntity living) {
                getLookControl().setLookAt(living);
                Vec3 head = living.getEyePosition();
                level.sendParticles(ParticleTypes.CLOUD, head.x, head.y, head.z, 8, 0.25, 0.2, 0.25, 0.01);
                // The grey shimmer of a memory charm: the mod's own mist, not vanilla's reserved enchant glyphs.
                level.sendParticles(new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(
                                at.koopro.wizardsandbeasts.registry.ModParticles.MIST.get(), 0xFFB8BCC8),
                        head.x, head.y, head.z, 16, 0.3, 0.3, 0.3, 0.02);
            }
        }
        level.playSound(null, blockPosition(), ModSounds.SPELL_CAST_CHARM.get(), SoundSource.NEUTRAL, 0.8f, 0.8f);
        // Officials who saw it keep their report; every Muggle forgets.
        java.util.Set<UUID> officials = new java.util.HashSet<>();
        for (UUID id : witnesses) {
            Entity witness = level.getEntity(id);
            if (witness != null && witness.getType().is(
                    at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceTags.MINISTRY_OFFICIALS)) {
                officials.add(id);
            }
        }
        MinistryCaseData.get(level.getServer()).forgetWitnesses(incidentId, id -> !officials.contains(id));
        ServerPlayer caster = casterId == null ? null : level.getServer().getPlayerList().getPlayer(casterId);
        if (caster != null && caster.level() == level && caster.distanceToSqr(this) <= SPEAKING_RANGE * SPEAKING_RANGE) {
            caster.sendSystemMessage(Component.translatable("ministry.wizards_and_beasts.obliviator.arrive",
                    getDisplayName()));
            caster.sendSystemMessage(Component.translatable("ministry.wizards_and_beasts.obliviator.reminder"));
        }
    }

    private void disapparate(ServerLevel level) {
        crack(level, position());
        discard();
    }

    private static void crack(ServerLevel level, Vec3 at) {
        level.playSound(null, BlockPos.containing(at), ModSounds.APPARITION_CRACK_WIZARD.get(), SoundSource.NEUTRAL,
                1.0f, 1.0f);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.02);
    }

    /** The incident she was sent for. */
    public long incidentId() {
        return incidentId;
    }

    /** Whether she has done her work and is about to go. */
    public boolean hasObliviated() {
        return age >= OBLIVIATE_AT;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Not a fight: an Obliviator struck at simply leaves.
        if (!isRemoved()) {
            disapparate(level);
        }
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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
}
