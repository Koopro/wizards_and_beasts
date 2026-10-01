package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Carries a beam in the world so the normal entity-render dispatch draws it. Purely client-side:
 * never spawned by the server, never synced, never saved. The renderer re-derives origin/target
 * every frame (see {@link BeamEntityRenderer}), so this only tracks growth ({@code progress}) and
 * lifetime.
 *
 * <p>The {@code EntityType} is registered on the common side (a renderer needs it), but nothing
 * server-side ever instantiates this class.
 */
public class BeamEntity extends Entity {

    /**
     * Client entity ids count <strong>down</strong> from -1. Vanilla hands out ids monotonically
     * from 0 upward, so a negative id can never be one the server sent — two beams (or a beam and a
     * real entity) can never collide in the client entity map.
     */
    private static final AtomicInteger CLIENT_ID = new AtomicInteger(-1);

    private int casterId = -1;
    private BeamStyle style = BeamStyle.laser(0x44CCFF);
    private BeamShape shape = new Laser();
    private Vec3 target = Vec3.ZERO;
    /** Which spell is being channelled, so a re-announcement can tell "same beam" from "new beam". */
    private String spellId = "";
    /** Reach in blocks as the server resolved it (spell range x wand range stat). */
    private float range = 32f;

    private float progress;
    private float progressPrev;
    /** Progress gained per tick while active. {@code <= 0} snaps instantly to full/empty. */
    private float extendSpeed;
    private boolean active = true;

    /** Ticks over which the beam brightens to full after it appears. Visual only. */
    private int fadeInTicks;
    /** Ticks it keeps drawing, dimming, after the channel ended. Visual only: damage stopped with the channel. */
    private int fadeOutTicks;
    private int fadeOutLeft;
    /** Reach when the channel ended; a fading beam never grows past it. */
    private float stoppedReach = -1f;

    /** A preview beam draws whatever this supplies each frame instead of its configured look. */
    private @Nullable Supplier<BeamAppearance.Appearance> liveLook;
    /** A preview beam discards itself at this tick unless kept alive; -1 = no limit (channel beams). */
    private int expiresAt = -1;

    public BeamEntity(EntityType<? extends BeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        // Frustum culling is disabled in BeamEntityRenderer#affectedByCulling instead of a
        // noCulling field: the beam stretches far past its tiny hitbox, so culling it against that
        // hitbox would drop it the moment the anchor left the frame.
        setId(CLIENT_ID.getAndDecrement());
    }

    @Override
    public void tick() {
        super.tick();
        Entity caster = level().getEntity(casterId);
        if (caster == null || !caster.isAlive()) {
            discard();
            return;
        }
        // Sit on the caster so culling and distance checks use a sensible reference point.
        setPos(caster.getX(), caster.getY(), caster.getZ());

        if (expiresAt >= 0 && tickCount >= expiresAt) {
            discard();
            return;
        }

        progressPrev = progress;
        if (!active && fadeOutLeft > 0) {
            // Fading out: hold the drawn length, dim the light (fadeAlpha), then go.
            fadeOutLeft--;
            if (fadeOutLeft <= 0) {
                discard();
            }
            return;
        }
        if (extendSpeed <= 0f) {
            progress = active ? 1f : 0f;
        } else {
            progress = Mth.clamp(progress + (active ? extendSpeed : -extendSpeed), 0f, 1f);
        }

        if (!active && progress <= 0f) {
            discard();
        }
    }

    /** 0..1 brightness from the fade-in and fade-out windows. */
    public float fadeAlpha(float partialTick) {
        float alpha = 1f;
        if (fadeInTicks > 0) {
            alpha = Math.min(1f, (tickCount + partialTick) / fadeInTicks);
        }
        if (!active && fadeOutTicks > 0) {
            alpha *= Mth.clamp((fadeOutLeft - partialTick) / fadeOutTicks, 0f, 1f);
        }
        return alpha;
    }

    public float getProgress(float partialTick) {
        return Mth.lerp(partialTick, progressPrev, progress);
    }

    /**
     * True once the beam has fully reached its target. Gameplay damage must gate on this same
     * condition the optics use — while {@code progress < 1} the beam has not arrived and must not
     * hit.
     */
    public boolean hasArrived() {
        return progress >= 1f;
    }

    // ── config ───────────────────────────────────────────────────────────────

    public void configure(int casterId, BeamStyle style, BeamShape shape, float extendSpeed) {
        this.casterId = casterId;
        this.style = style;
        this.shape = shape;
        this.extendSpeed = extendSpeed;
    }

    /** Swaps the look of a live beam (the server's visuals changed); its caster, reach and age are kept. */
    public void restyle(BeamAppearance.Appearance look) {
        this.style = look.style();
        this.shape = look.shape();
        this.fadeInTicks = Math.max(0, look.fadeInTicks());
        this.fadeOutTicks = Math.max(0, look.fadeOutTicks());
    }

    public void setActive(boolean active) {
        if (this.active && !active) {
            stoppedReach = currentReach(0f);
            fadeOutLeft = fadeOutTicks;
        }
        this.active = active;
    }

    public void setLiveLook(@Nullable Supplier<BeamAppearance.Appearance> liveLook) {
        this.liveLook = liveLook;
    }

    public BeamAppearance.@Nullable Appearance liveLook() {
        return liveLook == null ? null : liveLook.get();
    }

    /** Keeps a preview beam for {@code ticks} more ticks. */
    public void keepAliveFor(int ticks) {
        expiresAt = tickCount + ticks;
    }

    public void setTarget(Vec3 target) {
        this.target = target;
    }

    public String getSpellId() {
        return spellId;
    }

    public void setSpellId(String spellId) {
        this.spellId = spellId;
    }

    public float getRange() {
        return range;
    }

    public void setRange(float range) {
        this.range = range;
    }

    /**
     * How far the beam currently reaches, mirroring the server's ramp in
     * {@code WandBeamChannelLogic}: it grows at a fixed number of blocks per tick until it hits the
     * spell's range. Drawing anything else would put the visible end somewhere other than where
     * damage resolves.
     */
    public float currentReach(float partialTick) {
        float elapsed = tickCount + partialTick;
        float reach = Math.min(range, elapsed * BeamRayResolver.extensionBlocksPerTick());
        return stoppedReach >= 0f ? Math.min(stoppedReach, reach) : reach;
    }

    public int getCasterId() {
        return casterId;
    }

    public BeamStyle getStyle() {
        return style;
    }

    public BeamShape getShape() {
        return shape;
    }

    public Vec3 getTarget() {
        return target;
    }

    /** Next descending client-only entity id, for spawn code to assign before {@code addEntity}. */
    public static int nextClientId() {
        return CLIENT_ID.getAndDecrement();
    }

    // ── Entity plumbing (client-only ⇒ no synced/saved data) ──────────────────

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // no synced data — this entity never touches the network
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // never saved
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // never saved
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // never spawned server-side; nothing can damage a beam
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return true;
    }
}
