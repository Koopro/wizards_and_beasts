package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Signs a creature leaves behind: a tuft of hair where a unicorn grazed, a feather where a phoenix passed, dung
 * where mooncalves danced. The item on the ground carries an entity tag naming the species, so picking it up is a
 * naturalist's study of that species — the item itself is ordinary and stacks with any other.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CreatureSign {

    private static final String TAG_PREFIX = WizardsAndBeastsMod.MODID + ".sign.";

    /** How far a sign already lying on the ground stops another being left. */
    public static final double SIGN_SPACING = 8.0;

    private CreatureSign() {}

    /**
     * Leaves {@code count} of {@code item} at the creature's feet, marked as a sign of its species — unless one of its
     * signs of that item is already lying within {@link #SIGN_SPACING}.
     *
     * @return the item entity left, or {@code null} when none was
     */
    public static @Nullable ItemEntity leave(Entity creature, Item item, int count) {
        if (!(creature.level() instanceof ServerLevel level) || count <= 0) {
            return null;
        }
        if (lyingNearby(creature, item)) {
            return null;
        }
        ItemEntity sign = new ItemEntity(level, creature.getX(), creature.getY() + 0.2, creature.getZ(),
                new ItemStack(item, count));
        sign.setDefaultPickUpDelay();
        sign.addTag(tagFor(creature.getType()));
        level.addFreshEntity(sign);
        return sign;
    }

    /** Whether a sign of this creature's species and this item already lies nearby. */
    public static boolean lyingNearby(Entity creature, Item item) {
        String tag = tagFor(creature.getType());
        AABB area = creature.getBoundingBox().inflate(SIGN_SPACING);
        return !creature.level().getEntitiesOfClass(ItemEntity.class, area,
                found -> found.isAlive() && found.getTags().contains(tag) && found.getItem().is(item)).isEmpty();
    }

    /** The species a sign on the ground belongs to, or {@code null} for an ordinary item. */
    public static @Nullable EntityType<?> speciesOf(ItemEntity item) {
        for (String tag : item.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                Identifier id = Identifier.tryParse(tag.substring(TAG_PREFIX.length()).replace('/', ':'));
                return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            }
        }
        return null;
    }

    static String tagFor(EntityType<?> species) {
        return TAG_PREFIX + BuiltInRegistries.ENTITY_TYPE.getKey(species).toString().replace(':', '/');
    }

    /** Picking up what a creature left behind is studying it. */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        EntityType<?> species = speciesOf(event.getItemEntity());
        if (species != null) {
            BestiaryDiscoveryHandler.studied(player, species);
        }
    }
}
