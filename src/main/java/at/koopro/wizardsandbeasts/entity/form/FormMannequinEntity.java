package at.koopro.wizardsandbeasts.entity.form;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Armor-stand-like entity that displays a form model for debug purposes.
 * Spawned by the Morphologist's Wand. Has no AI and does not move.
 */
public class FormMannequinEntity extends Mob {

    private static final EntityDataAccessor<String> FORM_ID =
            SynchedEntityData.defineId(FormMannequinEntity.class, EntityDataSerializers.STRING);

    public FormMannequinEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public FormMannequinEntity(ServerLevel level, double x, double y, double z) {
        this(ModEntities.FORM_MANNEQUIN.get(), level);
        this.setPos(x, y, z);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FORM_ID, "human_default");
    }

    public String getFormId() {
        return this.entityData.get(FORM_ID);
    }

    public void setFormId(String formId) {
        this.entityData.set(FORM_ID, formId);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("FormId", Codec.STRING, getFormId());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("FormId", Codec.STRING).ifPresent(this::setFormId);
    }

    @Override
    protected void registerGoals() {
        // No AI — mannequin is static
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
