package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class WandAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WizardsAndBeastsMod.MODID);

    public record BondedWandRecord(
            Identifier woodKey,
            Identifier coreKey,
            WandFlexibility flexibility,
            float allegianceScore) {
        public static final Codec<BondedWandRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("wood_key").forGetter(BondedWandRecord::woodKey),
                Identifier.CODEC.fieldOf("core_key").forGetter(BondedWandRecord::coreKey),
                WandFlexibility.CODEC.fieldOf("flexibility").forGetter(BondedWandRecord::flexibility),
                Codec.FLOAT.fieldOf("allegiance_score").forGetter(BondedWandRecord::allegianceScore)
        ).apply(instance, BondedWandRecord::new));
    }

    public static final Supplier<AttachmentType<Optional<BondedWandRecord>>> BONDED_WAND =
            ATTACHMENTS.register("bonded_wand", () -> AttachmentType.builder(Optional::<BondedWandRecord>empty)
                    .serialize(BondedWandRecord.CODEC.optionalFieldOf("bonded_wand"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<Map<String, Float>>> WAND_RESONANCE_CACHE =
            ATTACHMENTS.register("wand_resonance_cache", () -> AttachmentType.builder((java.util.function.Supplier<Map<String, Float>>) HashMap::new)
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("entries"))
                    .build());

    private WandAttachments() {
    }
}
