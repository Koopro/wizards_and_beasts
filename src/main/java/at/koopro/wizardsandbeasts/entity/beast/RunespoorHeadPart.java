package at.koopro.wizardsandbeasts.entity.beast;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One of the Runespoor's three heads — a pure hit-detection hitbox, not independently rendered
 * (GeckoLib multi-part entities are drawn once, through the parent's own model with three head bones;
 * confirmed no per-part renderer dispatch exists in this render pipeline). Forwards all damage to the
 * parent, which routes it by <em>which</em> head was hit (plan/judge/critic) and owns the real health pool.
 */
public class RunespoorHeadPart extends PartEntity<RunespoorEntity> {

    private final String partId;
    private final EntityDimensions partDimensions;

    public RunespoorHeadPart(RunespoorEntity parent, String partId, float width, float height) {
        super(parent);
        this.partId = partId;
        this.partDimensions = EntityDimensions.scalable(width, height);
    }

    public String partId() {
        return partId;
    }

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return partDimensions;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return getParent().hurtFromHead(this, level, source, amount);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // No part-specific synced state; the parent owns all visual/health state.
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
    }
}
