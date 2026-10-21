package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Ids of the abilities that ship with real behaviors, shared by the datapack definitions, the behavior
 * registrations and the grant source. Keeping them in one place is what makes the grant source and the
 * {@code data/wizards_and_beasts/abilities/*.json} file names verifiably the same key.
 */
@NullMarked
public final class AbilityIds {

    public static final Identifier APPARITION = id("apparition");
    public static final Identifier APPARITION_TRAVEL = id("apparition_travel");
    public static final Identifier LEGILIMENCY = id("legilimency");
    public static final Identifier ANIMAGUS_FORM = id("animagus_form");
    public static final Identifier ANIMAGUS_BEAST_ABILITY = id("animagus_beast_ability");
    public static final Identifier OBSCURIAL_FORM = id("obscurial_form");
    /** Veela wings, taken by choice. The fury path in {@code VeelaHeritageHandler} needs no ability. */
    public static final Identifier VEELA_FORM = id("veela_form");
    public static final Identifier VEELA_ALLURE = id("veela_allure");
    /** A vampire's bat shape. Purely voluntary; nothing forces it. */
    public static final Identifier VAMPIRE_FORM = id("vampire_form");
    /** House-elf magic and service (heritage.elf). */
    public static final Identifier ELF_SNAP = id("elf_snap");
    public static final Identifier ELF_KITCHEN = id("elf_kitchen");
    public static final Identifier CALL_HOUSE_ELF = id("call_house_elf");
    /** A centaur's reading of the night sky (heritage.centaur.StarReading). */
    public static final Identifier STAR_READING = id("star_reading");
    public static final Identifier OBSCURIAL_STRESS_VENT = id("obscurial_stress_vent");
    public static final Identifier OBSCURUS_SURGE = id("obscurus_surge");
    public static final Identifier OBSCURUS_GRASP = id("obscurus_grasp");

    private AbilityIds() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }
}
