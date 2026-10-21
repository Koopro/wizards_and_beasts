package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * An uncommon way a wandmaker turns this wood: a silver ferrule, a bound grip, a carved tip.
 *
 * <p>Purely a look. Which wands get one is decided by the wand's own make (see
 * {@code WandLookResolver}), so the same wand always shows the same construction and no new data is
 * written to the stack. A construction only replaces the slots it names; the rest stay the wood's.
 *
 * @param chance   share of this wood's wands built this way, {@code (0, 1]}; a wood's constructions
 *                 together may not exceed 1
 * @param handle   replacement handle module
 * @param shaft    replacement shaft module
 * @param tip      replacement tip module
 * @param ornament ornament added to the grip
 */
public record WandRareConstruction(float chance,
                                   Optional<Identifier> handle,
                                   Optional<Identifier> shaft,
                                   Optional<Identifier> tip,
                                   Optional<Identifier> ornament) {

    public static final Codec<WandRareConstruction> CODEC = RecordCodecBuilder.<WandRareConstruction>create(instance -> instance.group(
            Codec.FLOAT.fieldOf("chance").forGetter(WandRareConstruction::chance),
            Identifier.CODEC.optionalFieldOf("handle").forGetter(WandRareConstruction::handle),
            Identifier.CODEC.optionalFieldOf("shaft").forGetter(WandRareConstruction::shaft),
            Identifier.CODEC.optionalFieldOf("tip").forGetter(WandRareConstruction::tip),
            Identifier.CODEC.optionalFieldOf("ornament").forGetter(WandRareConstruction::ornament)
    ).apply(instance, WandRareConstruction::new)).validate(WandRareConstruction::validate);

    /** The slots this construction decides; slots it leaves open are absent. */
    public Map<WandSlot, Identifier> modules() {
        Map<WandSlot, Identifier> modules = new EnumMap<>(WandSlot.class);
        handle.ifPresent(id -> modules.put(WandSlot.HANDLE, id));
        shaft.ifPresent(id -> modules.put(WandSlot.SHAFT, id));
        tip.ifPresent(id -> modules.put(WandSlot.TIP, id));
        ornament.ifPresent(id -> modules.put(WandSlot.ORNAMENT, id));
        return modules;
    }

    private static DataResult<WandRareConstruction> validate(WandRareConstruction construction) {
        if (!(construction.chance > 0.0f && construction.chance <= 1.0f)) {
            return DataResult.error(() -> "Rare wand construction chance must be in (0, 1]: " + construction.chance);
        }
        if (construction.modules().isEmpty()) {
            return DataResult.error(() -> "Rare wand construction changes nothing: name a handle, shaft, tip or ornament");
        }
        return DataResult.success(construction);
    }
}
