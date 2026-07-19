package at.koopro.wizardsandbeasts.ability.def;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Datapack-loaded description of one ability, mirroring the {@code BroomDefinition} pattern. Pure metadata —
 * no behavior. Loaded from {@code data/<ns>/abilities/*.json} by {@link AbilityDefinitionLoader}; the
 * {@code id} is taken from the file path (not the JSON body), so definitions are authored id-free and
 * stamped via {@link #withId(Identifier)} at load time.
 *
 * <p>Translatable copy is stored as keys only (like {@code BestiaryEntry}) — no lore text is authored here.
 *
 * @param id             identity; stamped from the file path at load (JSON body omits it)
 * @param type           {@link AbilityType} — governs wheel eligibility and trigger semantics
 * @param icon           texture {@link Identifier} for the wheel entry (placeholder acceptable)
 * @param nameKey        translation key for the display name
 * @param descriptionKey translation key for the description
 * @param module         optional {@code ModuleManager} module gating usability/visibility; {@code null} = always accessible
 * @param cooldownTicks  framework-tracked cooldown; ACTIVE/TOGGLE only; clamped {@code >= 0}
 * @param sortOrder      stable wheel ordering key (ascending), ties broken by id
 */
@NullMarked
public record AbilityDefinition(
        Identifier id,
        AbilityType type,
        Identifier icon,
        String nameKey,
        String descriptionKey,
        @Nullable Identifier module,
        int cooldownTicks,
        int sortOrder) {

    /** Placeholder used by {@link #CODEC} before the loader stamps the real file id via {@link #withId}. */
    private static final Identifier UNSET_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "unset");

    public AbilityDefinition {
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public static final Codec<AbilityDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AbilityType.CODEC.fieldOf("type").forGetter(AbilityDefinition::type),
            Identifier.CODEC.fieldOf("icon").forGetter(AbilityDefinition::icon),
            Codec.STRING.fieldOf("nameKey").forGetter(AbilityDefinition::nameKey),
            Codec.STRING.fieldOf("descriptionKey").forGetter(AbilityDefinition::descriptionKey),
            Identifier.CODEC.optionalFieldOf("module").forGetter(d -> Optional.ofNullable(d.module())),
            Codec.INT.optionalFieldOf("cooldownTicks", 0).forGetter(AbilityDefinition::cooldownTicks),
            Codec.INT.optionalFieldOf("sortOrder", 0).forGetter(AbilityDefinition::sortOrder)
    ).apply(instance, (type, icon, nameKey, descriptionKey, module, cooldownTicks, sortOrder) ->
            new AbilityDefinition(UNSET_ID, type, icon, nameKey, descriptionKey,
                    module.orElse(null), cooldownTicks, sortOrder)));

    /** Returns a copy with {@code id} stamped from the datapack file path. */
    public AbilityDefinition withId(Identifier fileId) {
        return new AbilityDefinition(fileId, type, icon, nameKey, descriptionKey, module, cooldownTicks, sortOrder);
    }

    /** True for ACTIVE/TOGGLE — the only types shown in and driven from the wheel. */
    public boolean isWheelEligible() {
        return type.isWheelEligible();
    }

    public Component displayName() {
        return Component.translatable(nameKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }
}
