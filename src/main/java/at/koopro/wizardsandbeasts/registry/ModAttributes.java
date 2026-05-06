package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
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
}
