package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<Attribute, Attribute> DARK_CORRUPTION = ATTRIBUTES.register(
            "dark_corruption",
            () -> new RangedAttribute("attribute.wizards_and_beasts.dark_corruption", 0.0d, 0.0d, 100.0d).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> WAND_AFFINITY = ATTRIBUTES.register(
            "wand_affinity",
            () -> new RangedAttribute("attribute.wizards_and_beasts.wand_affinity", 1.0d, 0.5d, 2.0d).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> BEAST_RESISTANCE = ATTRIBUTES.register(
            "beast_resistance",
            () -> new RangedAttribute("attribute.wizards_and_beasts.beast_resistance", 0.0d, 0.0d, 1.0d).setSyncable(true));

    private ModAttributes() {
    }

    /**
     * Attaches {@link #DARK_CORRUPTION} to the player. It is registered in the attribute registry but
     * belongs to no entity until this runs, so without it {@code player.getAttribute(DARK_CORRUPTION)}
     * returns {@code null} and the character sheet's Dark Corruption bar always reads 0.
     * {@link at.koopro.wizardsandbeasts.corruption.DarkCorruptionService} mirrors the persisted
     * corruption value onto this Attribute so the (syncable) value reaches the client for free.
     *
     * <p>{@link #WAND_AFFINITY} and {@link #BEAST_RESISTANCE} are intentionally left off the player for
     * now: nothing writes their base value, so attaching them would only paint a static default bar with
     * no mechanic behind it. Add them here once a real writer exists. Wired to
     * {@link EntityAttributeModificationEvent} in {@code WizardsAndBeastsMod}.
     */
    public static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, DARK_CORRUPTION)) {
            event.add(EntityType.PLAYER, DARK_CORRUPTION);
        }
    }
}
