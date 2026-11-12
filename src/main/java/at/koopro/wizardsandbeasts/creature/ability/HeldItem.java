package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * The creature carries a real item in its main hand, drawn by {@code BeastHeldItemLayer} at its rig's
 * {@code held_item} bone: a merperson's trident. The owner's rule (2026-11-12): a creature that carries something
 * Minecraft already has as an item holds that item, the way a skeleton holds its bow, and no lookalike is modelled.
 *
 * <p>It is put in the hand on the server tick whenever the hand is empty, so a creature saved before it carried one
 * picks it up on load. It is never dropped. Archers get their bow from {@link RangedShot} instead.
 *
 * @param item the item's id, e.g. {@code minecraft:trident}
 */
public record HeldItem(Identifier item) implements CreatureAbility {

    public static final MapCodec<HeldItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(HeldItem::item)
    ).apply(instance, HeldItem::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.HELD_ITEM;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!entity.getMainHandItem().isEmpty() || entity.tickCount % 20 != 0) {
            return;
        }
        Item held = BuiltInRegistries.ITEM.getValue(item);
        if (held != null && held != net.minecraft.world.item.Items.AIR) {
            entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(held));
            entity.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }
    }
}
