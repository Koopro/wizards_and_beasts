package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.azkaban.attachment.AzkabanTrespasserData;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.bloodpact.BloodPactRecord;
import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.event.wand.DisarmLogState;
import at.koopro.wizardsandbeasts.entity.niffler.HappinessAttachment;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState;
import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityProficiency;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.memory.PlayerMemoryData;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.vocation.PlayerVocationData;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WizardsAndBeastsMod.MODID);

    public static final Supplier<AttachmentType<PlayerSpellData>> SPELL_DATA =
            registerData("spell_data", PlayerSpellData::new);

    public static final Supplier<AttachmentType<PlayerVaultData>> VAULT_DATA =
            registerData("vault_data", PlayerVaultData::new);

    /** Registry id {@code type_data} is legacy; kept for world save compatibility — use {@code HERITAGE_DATA} in Java. */
    public static final Supplier<AttachmentType<PlayerHeritageData>> HERITAGE_DATA =
            registerData("type_data", PlayerHeritageData::new);

    public static final Supplier<AttachmentType<PlayerSkillData>> SKILL_DATA =
            registerData("skill_data", PlayerSkillData::new);

    /** Committed Vocation slots — separate from SKILL_DATA; absent on existing players → both empty. */
    public static final Supplier<AttachmentType<PlayerVocationData>> VOCATION_DATA =
            ATTACHMENTS.register("vocation_data", () -> AttachmentType.builder(() -> PlayerVocationData.EMPTY)
                    .serialize(PlayerVocationData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<PlayerSkillBonusData>> SKILL_BONUS_DATA =
            ATTACHMENTS.register("skill_bonus_data", () -> AttachmentType.builder(() -> PlayerSkillBonusData.DEFAULT)
                    .serialize(PlayerSkillBonusData.CODEC.fieldOf("bonus"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<PlayerBestiaryData>> BESTIARY_DATA =
            ATTACHMENTS.register("bestiary_data", () -> AttachmentType.builder(() -> new PlayerBestiaryData(new HashMap<>()))
                    .serialize(PlayerBestiaryData.CODEC.fieldOf("bestiary"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<PlayerAbilityData>> PLAYER_ABILITY_DATA =
            ATTACHMENTS.register("player_ability_data", () -> AttachmentType.builder(() -> PlayerAbilityData.DEFAULT)
                    .serialize(PlayerAbilityData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    /**
     * Generic ability-wheel/selection state (selected/pinned/toggles/cooldowns). Deliberately NOT
     * {@code copyOnDeath}: {@code AbilityFrameworkEvents.onPlayerClone} copies selection/pin/toggles and
     * drops cooldowns on death (§2.2). Disk persistence is via the serialize codec, independent of clone.
     */
    public static final Supplier<AttachmentType<AbilitySelectionState>> ABILITY_SELECTION =
            ATTACHMENTS.register("ability_selection", () -> AttachmentType.builder(() -> AbilitySelectionState.EMPTY)
                    .serialize(AbilitySelectionState.CODEC.fieldOf("data"))
                    .build());

    /**
     * Per-ability practice, keyed by ability {@link net.minecraft.resources.Identifier}. {@code copyOnDeath}:
     * dying costs you your gear, not your training. Separate from the per-spell proficiency on
     * {@code SPELL_DATA}, which only spell hits can write and {@code Module.PROFICIENCY} can switch off.
     */
    public static final Supplier<AttachmentType<PlayerAbilityProficiency>> ABILITY_PROFICIENCY =
            ATTACHMENTS.register("ability_proficiency", () -> AttachmentType.builder(() -> PlayerAbilityProficiency.EMPTY)
                    .serialize(PlayerAbilityProficiency.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    /**
     * The player's Ministry record: notoriety, criminal file, sentence, rank. {@code copyOnDeath} — the
     * Ministry does not lose your file because you died.
     */
    public static final Supplier<AttachmentType<PlayerMinistryRecord>> MINISTRY_RECORD =
            ATTACHMENTS.register("ministry_record", () -> AttachmentType.builder(() -> PlayerMinistryRecord.DEFAULT)
                    .serialize(PlayerMinistryRecord.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    /**
     * Destinations the player has memorised for Apparition. {@code copyOnDeath}: dying does not make a
     * wizard forget where places are.
     */
    public static final Supplier<AttachmentType<PlayerApparitionPoints>> APPARITION_POINTS =
            ATTACHMENTS.register("apparition_points", () -> AttachmentType.builder(() -> PlayerApparitionPoints.EMPTY)
                    .serialize(PlayerApparitionPoints.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<PlayerOWLData>> OWL_DATA =
            ATTACHMENTS.register("owl_data", () -> AttachmentType.builder(() -> PlayerOWLData.DEFAULT)
                    .serialize(PlayerOWLData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<PlayerProfessionData>> PROFESSION_DATA =
            ATTACHMENTS.register("profession_data", () -> AttachmentType.builder(() -> PlayerProfessionData.DEFAULT)
                    .serialize(PlayerProfessionData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<Float>> HAPPINESS =
            ATTACHMENTS.register("happiness", () -> AttachmentType.builder(() -> HappinessAttachment.DEFAULT)
                    .serialize(Codec.floatRange(0f, 100f).fieldOf("happiness"))
                    .copyOnDeath()
                    .build());

    /** Cumulative Dark Arts use; never resets passively. */
    public static final Supplier<AttachmentType<Float>> DARK_CORRUPTION =
            ATTACHMENTS.register("dark_corruption", () -> AttachmentType.builder(() -> 0.0f)
                    .serialize(Codec.floatRange(0f, 100f).fieldOf("value"))
                    .copyOnDeath()
                    .build());

    /** True once a wizard bears the Dark Mark — a branded Death Eater, callable through the Mark. */
    public static final Supplier<AttachmentType<Boolean>> DARK_MARK =
            ATTACHMENTS.register("dark_mark", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("marked"))
                    .copyOnDeath()
                    .build());

    /** Cognitive integrity; regenerates when not under harmful mental effects. */
    public static final Supplier<AttachmentType<Float>> MENTAL_STABILITY =
            ATTACHMENTS.register("mental_stability", () -> AttachmentType.builder(() -> 100.0f)
                    .serialize(Codec.floatRange(0f, 100f).fieldOf("value"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<ImperioControlState>> IMPERIO_CONTROL_STATE =
            ATTACHMENTS.register("imperio_control_state", () -> AttachmentType.builder(() -> ImperioControlState.DEFAULT)
                    .serialize(ImperioControlState.CODEC.fieldOf("state"))
                    .copyOnDeath()
                    .build());

    /** Basilisk-gaze petrification. Not copied on death — dying naturally breaks the curse. */
    public static final Supplier<AttachmentType<at.koopro.wizardsandbeasts.spell.petrify.PetrifiedState>> PETRIFIED_STATE =
            ATTACHMENTS.register("petrified_state", () -> AttachmentType.builder(() -> at.koopro.wizardsandbeasts.spell.petrify.PetrifiedState.DEFAULT)
                    .serialize(at.koopro.wizardsandbeasts.spell.petrify.PetrifiedState.CODEC.fieldOf("state"))
                    .build());

    public static final Supplier<AttachmentType<Float>> WILLPOWER =
            ATTACHMENTS.register("willpower", () -> AttachmentType.builder(() -> 50.0f)
                    .serialize(Codec.floatRange(0f, 100f).fieldOf("value"))
                    .copyOnDeath()
                    .build());

    /** Future sacrifice mechanic; when true, blocks AK for a timed window. */
    public static final Supplier<AttachmentType<Boolean>> LOVE_PROTECTION =
            ATTACHMENTS.register("love_protection", () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("value"))
                    .copyOnDeath()
                    .build());

    /** Registry key string for Patronus form (e.g. minecraft:wolf). */
    public static final Supplier<AttachmentType<String>> PATRONUS_FORM =
            ATTACHMENTS.register("patronus_form", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("form"))
                    .copyOnDeath()
                    .build());

    /** Per-player disarm log for wand allegiance transfer. */
    public static final Supplier<AttachmentType<DisarmLogState>> DISARM_LOG =
            ATTACHMENTS.register("disarm_log", () -> AttachmentType.builder(() -> new DisarmLogState(new HashMap<>()))
                    .serialize(DisarmLogState.CODEC.fieldOf("log"))
                    .copyOnDeath()
                    .build());

    /** Cumulative ticks under Cruciatus sanity drain (Longbottom threshold). */
    public static final Supplier<AttachmentType<Integer>> CRUCIO_EXPOSURE_TICKS =
            ATTACHMENTS.register("crucio_exposure_ticks", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.xmap(v -> Math.max(0, v), v -> v).fieldOf("ticks"))
                    .copyOnDeath()
                    .build());

    /** Tracks the UUID of a Niffler currently carried in the player's pocket. Not persisted on death. */
    public static final Supplier<AttachmentType<CarriedNifflerAttachment>> CARRIED_NIFFLER =
            ATTACHMENTS.register("carried_niffler", () -> AttachmentType.builder(() -> CarriedNifflerAttachment.EMPTY)
                    .serialize(CarriedNifflerAttachment.CODEC.fieldOf("data"))
                    .build());

    public static final Supplier<AttachmentType<List<BloodPactRecord>>> ACTIVE_BLOOD_PACTS =
            ATTACHMENTS.register("active_blood_pacts", () ->
                    AttachmentType.<List<BloodPactRecord>>builder((Supplier<List<BloodPactRecord>>) ArrayList::new)
                            .serialize(BloodPactRecord.CODEC.listOf().fieldOf("pacts"))
                            .copyOnDeath()
                            .build());

    public static final Supplier<AttachmentType<AzkabanTrespasserData>> AZKABAN_TRESPASSER_TAG =
            ATTACHMENTS.register("azkaban_trespasser_tag", () ->
                    AttachmentType.builder(() -> AzkabanTrespasserData.DEFAULT)
                            .serialize(AzkabanTrespasserData.CODEC.fieldOf("data"))
                            .copyOnDeath()
                            .build());

    public static final Supplier<AttachmentType<PlayerStatsData>> PLAYER_STATS =
            ATTACHMENTS.register("player_stats", () -> AttachmentType.builder(() -> PlayerStatsData.EMPTY)
                    .serialize(PlayerStatsData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<Set<String>>> FLOO_VISITED_DESTINATIONS =
            ATTACHMENTS.register("floo_visited_destinations", () ->
                    AttachmentType.<Set<String>>builder(() -> new HashSet<>())
                            .serialize(Codec.list(Codec.STRING)
                                    .<Set<String>>xmap(list -> new HashSet<>(list), set -> List.copyOf(set))
                                    .fieldOf("visited"))
                            .copyOnDeath()
                            .build());

    public static final Supplier<AttachmentType<PlayerMemoryData>> MEMORIES =
            ATTACHMENTS.register("memories", () -> AttachmentType.builder(PlayerMemoryData::new)
                    .serialize(PlayerMemoryData.CODEC.fieldOf("data"))
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }

    /**
     * Generic registration for any player data type that implements save()/load().
     */
    private static <T extends NbtSerializable> Supplier<AttachmentType<T>> registerData(
            String name, Supplier<T> factory) {
        return ATTACHMENTS.register(name, () ->
                AttachmentType.builder(factory)
                        .serialize(new IAttachmentSerializer<T>() {
                            @Override
                            public T read(IAttachmentHolder holder, ValueInput input) {
                                T data = factory.get();
                                CompoundTag tag = input.read("data", CompoundTag.CODEC)
                                        .orElse(new CompoundTag());
                                data.load(tag);
                                return data;
                            }

                            @Override
                            public boolean write(T data, ValueOutput output) {
                                output.store("data", CompoundTag.CODEC, data.save());
                                return true;
                            }
                        })
                        .copyOnDeath()
                        .build());
    }

    /**
     * Interface for player data types that can serialize to/from CompoundTag.
     */
    public interface NbtSerializable {
        CompoundTag save();
        void load(CompoundTag tag);
    }
}
