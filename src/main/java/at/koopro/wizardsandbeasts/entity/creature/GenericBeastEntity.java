package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Shared base for data-configured GeckoLib creatures. The concrete {@code EntityType} is registered
 * per creature ({@code ModCreatures}); this class learns which creature it is at runtime from its
 * {@code EntityType} registry key, then applies its {@link CreatureDefinition} attributes server-side.
 *
 * <p>No bespoke per-creature subclass exists — only the four locomotion subclasses. No mutable entity
 * state is read at render time; the renderer resolves assets purely by the entity's id.
 */
public abstract class GenericBeastEntity extends GeoEntityBase {

    protected GenericBeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) {
            applyDefinition();
        }
    }

    /** The creature id == this entity type's registry key (e.g. {@code wizards_and_beasts:unicorn}). */
    public Identifier creatureId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType());
    }

    /** Short path used for the animation/model/texture name convention (e.g. {@code unicorn}). */
    protected String assetName() {
        return creatureId().getPath();
    }

    @Nullable
    protected CreatureDefinition definition() {
        return CreatureDefinitionRegistry.get(creatureId());
    }

    /** Override the registered baseline attributes with this creature's definition values (server-side). */
    protected void applyDefinition() {
        CreatureDefinition def = definition();
        if (def == null) {
            return;
        }
        setBase(Attributes.MAX_HEALTH, def.maxHealth());
        setBase(Attributes.MOVEMENT_SPEED, def.movementSpeed());
        setBase(Attributes.FOLLOW_RANGE, def.followRange());
        if (def.flyingSpeed() > 0) {
            setBase(Attributes.FLYING_SPEED, def.flyingSpeed());
        }
        setHealth(getMaxHealth());
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Common goals shared by every locomotion class. Subclasses add their wander/nav goals. */
    protected void registerCommonGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 7.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }
}
