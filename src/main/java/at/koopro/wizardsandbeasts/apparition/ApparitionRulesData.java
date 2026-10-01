package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

/**
 * Where a world keeps its Apparition rules. Absent fields read as the authored values, and stored values are clamped
 * on load, so a hand-edited save can never publish a rule the admin panel would have refused.
 */
@NullMarked
public final class ApparitionRulesData extends SavedData {

    private static final ApparitionRules.Tuning A = ApparitionRules.Tuning.AUTHORED;

    public static final SavedDataType<ApparitionRulesData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_apparition_rules",
            ApparitionRulesData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    WindupDamageMode.CODEC.optionalFieldOf("windup_damage_mode", A.windupDamageMode())
                            .forGetter(d -> d.tuning.windupDamageMode()),
                    Codec.INT.optionalFieldOf("blink_cooldown_ticks", A.blinkCooldownTicks())
                            .forGetter(d -> d.tuning.blinkCooldownTicks()),
                    Codec.INT.optionalFieldOf("anchored_cooldown_ticks", A.anchoredCooldownTicks())
                            .forGetter(d -> d.tuning.anchoredCooldownTicks()),
                    Codec.INT.optionalFieldOf("splinch_severity_percent", A.splinchSeverityPercent())
                            .forGetter(d -> d.tuning.splinchSeverityPercent()),
                    Codec.INT.optionalFieldOf("licence_proficiency_percent", A.licenceProficiencyPercent())
                            .forGetter(d -> d.tuning.licenceProficiencyPercent())
            ).apply(instance, (mode, blink, anchored, severity, licence) -> new ApparitionRulesData(
                    new ApparitionRules.Tuning(mode, clamp(blink, 0, ApparitionRules.MAX_COOLDOWN_TICKS),
                            clamp(anchored, 0, ApparitionRules.MAX_COOLDOWN_TICKS),
                            clamp(severity, 0, ApparitionRules.MAX_SPLINCH_SEVERITY_PERCENT),
                            clamp(licence, 0, 100))))));

    private ApparitionRules.Tuning tuning;

    private ApparitionRulesData() {
        this(ApparitionRules.Tuning.AUTHORED);
    }

    private ApparitionRulesData(ApparitionRules.Tuning tuning) {
        this.tuning = tuning;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static ApparitionRulesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public ApparitionRules.Tuning tuning() {
        return tuning;
    }

    public void set(ApparitionRules.Tuning next) {
        if (!next.equals(tuning)) {
            tuning = next;
            setDirty();
        }
    }
}
