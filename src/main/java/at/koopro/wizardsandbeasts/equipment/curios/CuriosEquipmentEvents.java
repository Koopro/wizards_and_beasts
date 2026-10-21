package at.koopro.wizardsandbeasts.equipment.curios;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.WearableDefinition;
import at.koopro.wizardsandbeasts.equipment.WearableDefinitions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jspecify.annotations.NullMarked;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * Turns a wearable definition's {@code attributes} into Curios attribute modifiers.
 *
 * <p>Event-driven rather than an {@code ICurioItem} on each item, so the items stay free of Curios
 * types and a datapack can give stats to any item it defines — including another mod's. Modifiers are
 * scoped to the definition's own slot, so a stack forced into some other slot by a command grants
 * nothing, and Curios applies them only for real (non-cosmetic) slots, server-side, which is what
 * keeps the client out of the gameplay path.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class CuriosEquipmentEvents {

    private CuriosEquipmentEvents() {}

    @SubscribeEvent
    public static void onCurioAttributeModifiers(CurioAttributeModifierEvent event) {
        WearableDefinition definition = WearableDefinitions.get(event.getItemStack());
        if (definition == null || definition.attributes().isEmpty()) {
            return;
        }
        String slot = definition.type().slotId();
        for (WearableDefinition.Modifier modifier : definition.attributes()) {
            event.addModifier(modifier.attribute(), modifier.toModifier(), slot);
        }
    }
}
