package at.koopro.wizardsandbeasts.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Static registry of all {@link PlayerForm} definitions.
 * <p>
 * Default forms are registered in the static initializer. Additional forms
 * can be registered via {@link #register(PlayerForm)} during mod init.
 */
public final class FormRegistry {

    private static final Map<String, PlayerForm> FORMS = new LinkedHashMap<>();

    static {
        // ── Wizardkind ──
        register(form("human_default", "Human", ModelType.HUMANOID,
                "entity/form/human_default", "wizardkind_default", EnumSet.noneOf(RenderFlag.class)));

        // ── Werewolf ──
        register(form("werewolf_human", "Werewolf (Human)", ModelType.HUMANOID,
                "entity/form/human_default", "werewolf_human", EnumSet.noneOf(RenderFlag.class)));
        register(form("werewolf_wolf", "Werewolf (Wolf)", ModelType.CUSTOM_BIPED,
                "entity/form/werewolf", "werewolf_wolf",
                EnumSet.of(RenderFlag.GLOWING_EYES, RenderFlag.TAIL_LAYER)));

        // ── Obscurial ──
        register(form("obscurial_human", "Obscurial (Human)", ModelType.HUMANOID,
                "entity/form/human_default", "obscurial_human", EnumSet.noneOf(RenderFlag.class)));
        register(form("obscurial_dark", "Obscurial (Dark)", ModelType.SHADOW,
                "entity/form/obscurial_dark", "obscurial_dark",
                EnumSet.of(RenderFlag.TRANSLUCENT, RenderFlag.SHADOW_OVERLAY, RenderFlag.PARTICLE_AURA)));

        // ── Goblin ──
        register(form("goblin_default", "Goblin", ModelType.SMALL_HUMANOID,
                "entity/form/goblin", "goblin_default", EnumSet.noneOf(RenderFlag.class)));

        // ── House-Elf ──
        register(form("house_elf_default", "House-Elf", ModelType.SMALL_HUMANOID,
                "entity/form/house_elf", "house_elf_default", EnumSet.noneOf(RenderFlag.class)));

        // ── Veela ──
        register(form("veela_human", "Veela (Human)", ModelType.HUMANOID,
                "entity/form/human_default", "veela_human", EnumSet.noneOf(RenderFlag.class)));
        register(form("veela_harpy", "Veela (Harpy)", ModelType.CUSTOM_BIPED,
                "entity/form/veela_harpy", "veela_harpy",
                EnumSet.of(RenderFlag.WING_LAYER)));

        // ── Giant ──
        register(form("giant_default", "Giant", ModelType.HUMANOID,
                "entity/form/human_default", "giant_full", EnumSet.noneOf(RenderFlag.class)));
        register(form("half_giant_default", "Half-Giant", ModelType.HUMANOID,
                "entity/form/human_default", "giant_half", EnumSet.noneOf(RenderFlag.class)));

        // ── Centaur ──
        register(form("centaur_default", "Centaur", ModelType.QUADRUPED,
                "entity/form/centaur", "centaur_default", EnumSet.of(RenderFlag.TAIL_LAYER)));

        // ── Vampire ──
        register(form("vampire_human", "Vampire (Human)", ModelType.HUMANOID,
                "entity/form/human_default", "vampire_default",
                EnumSet.of(RenderFlag.GLOWING_EYES)));
        register(form("vampire_bat", "Vampire (Bat)", ModelType.FLYING,
                "entity/form/bat_form", "vampire_bat", EnumSet.of(RenderFlag.WING_LAYER)));

        // ── Merpeople ──
        register(form("merfolk_land", "Merfolk (Land)", ModelType.HUMANOID,
                "entity/form/human_default", "merpeople_land", EnumSet.noneOf(RenderFlag.class)));
        register(form("merfolk_water", "Merfolk (Water)", ModelType.SWIMMING,
                "entity/form/merfolk", "merpeople_water",
                EnumSet.of(RenderFlag.TAIL_LAYER, RenderFlag.PARTICLE_AURA)));
    }

    private static PlayerForm form(String id, String displayName, ModelType modelType,
                                    String texturePath, String sizeProfileId,
                                    EnumSet<RenderFlag> flags) {
        return new PlayerForm(id, displayName, modelType,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, texturePath),
                sizeProfileId, flags, 1.0f);
    }

    public static void register(PlayerForm form) {
        FORMS.put(form.formId(), form);
    }

    @Nullable
    public static PlayerForm get(String formId) {
        return FORMS.get(formId);
    }

    public static PlayerForm getOrDefault(String formId) {
        PlayerForm form = FORMS.get(formId);
        return form != null ? form : FORMS.get("human_default");
    }

    /**
     * Returns all forms whose size profile matches profiles available to the given list of profile IDs.
     */
    public static List<PlayerForm> getFormsForProfileIds(List<String> profileIds) {
        return FORMS.values().stream()
                .filter(f -> profileIds.contains(f.sizeProfileId()))
                .collect(Collectors.toList());
    }

    public static Map<String, PlayerForm> getAll() {
        return Collections.unmodifiableMap(FORMS);
    }

    public static List<String> getAllFormIds() {
        return List.copyOf(FORMS.keySet());
    }

    private FormRegistry() {}
}
