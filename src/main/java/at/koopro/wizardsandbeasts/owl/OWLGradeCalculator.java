package at.koopro.wizardsandbeasts.owl;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class OWLGradeCalculator {

    // CHARMS — Spell Mastery tree nodes
    private static final int CHARMS_O = 20;
    private static final int CHARMS_E = 14;
    private static final int CHARMS_A = 8;

    // DEFENCE_AGAINST_DARK_ARTS — combat casts OR Dark Arts nodes
    private static final int DADA_CASTS_O = 500;
    private static final int DADA_CASTS_E = 300;
    private static final int DADA_CASTS_A = 100;
    private static final int DADA_NODES_O = 10;
    private static final int DADA_NODES_E = 6;
    private static final int DADA_NODES_A = 3;

    // TRANSFIGURATION — animagus stage (0-3) OR metamorphmagus forms used
    private static final int TRANS_STAGE_O = 3;
    private static final int TRANS_STAGE_E = 2;
    private static final int TRANS_STAGE_A = 1;
    private static final int TRANS_FORMS_O = 20;
    private static final int TRANS_FORMS_E = 10;
    private static final int TRANS_FORMS_A = 3;

    // POTIONS — complexity-weighted brew points
    private static final int POTIONS_O = 50;
    private static final int POTIONS_E = 30;
    private static final int POTIONS_A = 10;

    // HERBOLOGY — nodes AND plants harvested
    private static final int HERB_NODES_O = 15;
    private static final int HERB_NODES_E = 10;
    private static final int HERB_NODES_A = 5;
    private static final int HERB_PLANTS_O = 20;
    private static final int HERB_PLANTS_E = 12;
    private static final int HERB_PLANTS_A = 5;

    // CARE_OF_MAGICAL_CREATURES — magizoology nodes AND Tier3+ bestiary entries
    private static final int CMC_NODES_O = 10;
    private static final int CMC_NODES_E = 6;
    private static final int CMC_NODES_A = 3;
    private static final int CMC_ENTRIES_O = 10;
    private static final int CMC_ENTRIES_E = 5;
    private static final int CMC_ENTRIES_A = 2;
    private static final int BESTIARY_TIER3_INDEX = 3; // DiscoveryTier.STUDIED

    // ARITHMANCY — rune/puzzle interactions in worldgen structures
    private static final int ARITH_O = 30;
    private static final int ARITH_E = 15;
    private static final int ARITH_A = 5;

    // ANCIENT_RUNES — Wandlore nodes + runic item interactions
    private static final int RUNES_NODES_O = 12;
    private static final int RUNES_NODES_E = 7;
    private static final int RUNES_NODES_A = 3;

    // DIVINATION — predictions + crystal ball uses
    private static final int DIV_O = 20;
    private static final int DIV_E = 10;
    private static final int DIV_A = 3;

    // HISTORY_OF_MAGIC — lore plaques and books read
    private static final int HOM_O = 40;
    private static final int HOM_E = 20;
    private static final int HOM_A = 8;

    // ASTRONOMY — nights observing + moon phase tracking events
    private static final int ASTRO_O = 30;
    private static final int ASTRO_E = 15;
    private static final int ASTRO_A = 5;

    // MUGGLE_STUDIES — muggle items crafted or traded
    private static final int MUGGLE_O = 50;
    private static final int MUGGLE_E = 25;
    private static final int MUGGLE_A = 10;

    private OWLGradeCalculator() {}

    public static @NonNull Map<OWLSubject, OWLGrade> calculateAll(@NonNull ServerPlayer player) {
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        for (OWLSubject subject : OWLSubject.values()) {
            grades.put(subject, calculate(player, subject));
        }
        return Collections.unmodifiableMap(grades);
    }

    public static @NonNull OWLGrade calculate(@NonNull ServerPlayer player, @NonNull OWLSubject subject) {
        return switch (subject) {
            case CHARMS -> gradeCharms(player);
            case DEFENCE_AGAINST_DARK_ARTS -> gradeDADA(player);
            case TRANSFIGURATION -> gradeTransfiguration(player);
            case POTIONS -> gradePotions(player);
            case HERBOLOGY -> gradeHerbology(player);
            case CARE_OF_MAGICAL_CREATURES -> gradeCMC(player);
            case ARITHMANCY -> gradeArithmancy(player);
            case ANCIENT_RUNES -> gradeAncientRunes(player);
            case DIVINATION -> gradeDivination(player);
            case HISTORY_OF_MAGIC -> gradeHistoryOfMagic(player);
            case ASTRONOMY -> gradeAstronomy(player);
            case MUGGLE_STUDIES -> gradeMuggleStudies(player);
        };
    }

    private static OWLGrade gradeCharms(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int nodes = countTreeNodes(skillData, SkillTreeId.SPELL_MASTERY);
        return thresholdGrade(nodes, CHARMS_O, CHARMS_E, CHARMS_A);
    }

    private static OWLGrade gradeDADA(ServerPlayer player) {
        PlayerSpellData spellData = player.getData(ModAttachments.SPELL_DATA.get());
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int casts = spellData.getCombatSpellCasts(); // NEW FIELD
        int darkArtsNodes = countTreeNodes(skillData, SkillTreeId.DARK_ARTS);
        if (casts >= DADA_CASTS_O || darkArtsNodes >= DADA_NODES_O) return OWLGrade.O;
        if (casts >= DADA_CASTS_E || darkArtsNodes >= DADA_NODES_E) return OWLGrade.E;
        if (casts >= DADA_CASTS_A || darkArtsNodes >= DADA_NODES_A) return OWLGrade.A;
        int combined = casts + darkArtsNodes * 50;
        if (combined <= 0) return OWLGrade.T;
        if (combined < DADA_CASTS_A / 3) return OWLGrade.D;
        return OWLGrade.P;
    }

    private static OWLGrade gradeTransfiguration(ServerPlayer player) {
        PlayerAbilityData abilityData = player.getData(ModAttachments.PLAYER_ABILITY_DATA.get());
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int stage = 0;
        if (abilityData.animagusUnlocked()) stage = 1;
        if (abilityData.animagusRegistered()) stage = 2;
        if (abilityData.animagusUnlocked() && abilityData.animagusRegistered() && abilityData.currentlyTransformed()) stage = 3;
        int forms = skillData.getMetamorphFormsUsed(); // NEW FIELD
        if (stage >= TRANS_STAGE_O || forms >= TRANS_FORMS_O) return OWLGrade.O;
        if (stage >= TRANS_STAGE_E || forms >= TRANS_FORMS_E) return OWLGrade.E;
        if (stage >= TRANS_STAGE_A || forms >= TRANS_FORMS_A) return OWLGrade.A;
        int combined = stage + forms;
        if (combined <= 0) return OWLGrade.T;
        if (forms < TRANS_FORMS_A / 3) return OWLGrade.D;
        return OWLGrade.P;
    }

    private static OWLGrade gradePotions(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int points = skillData.getPotionBrewPoints(); // NEW FIELD
        return thresholdGrade(points, POTIONS_O, POTIONS_E, POTIONS_A);
    }

    private static OWLGrade gradeHerbology(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int nodes = countTreeNodes(skillData, SkillTreeId.HERBOLOGY);
        int plants = skillData.getPlantsHarvested(); // NEW FIELD
        if (nodes >= HERB_NODES_O && plants >= HERB_PLANTS_O) return OWLGrade.O;
        if (nodes >= HERB_NODES_E && plants >= HERB_PLANTS_E) return OWLGrade.E;
        if (nodes >= HERB_NODES_A && plants >= HERB_PLANTS_A) return OWLGrade.A;
        int nodePct = HERB_NODES_A > 0 ? nodes * 100 / HERB_NODES_A : 0;
        int plantPct = HERB_PLANTS_A > 0 ? plants * 100 / HERB_PLANTS_A : 0;
        int combined = (nodePct + plantPct) / 2;
        if (combined <= 0) return OWLGrade.T;
        if (combined < 33) return OWLGrade.D;
        return OWLGrade.P;
    }

    private static OWLGrade gradeCMC(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        PlayerBestiaryData bestiaryData = player.getData(ModAttachments.BESTIARY_DATA.get());
        int nodes = countTreeNodes(skillData, SkillTreeId.MAGIZOOLOGY);
        long tier3Entries = bestiaryData.tiers().values().stream()
                .filter(t -> t.tierIndex() >= BESTIARY_TIER3_INDEX)
                .count();
        int entries = (int) tier3Entries;
        if (nodes >= CMC_NODES_O && entries >= CMC_ENTRIES_O) return OWLGrade.O;
        if (nodes >= CMC_NODES_E && entries >= CMC_ENTRIES_E) return OWLGrade.E;
        if (nodes >= CMC_NODES_A && entries >= CMC_ENTRIES_A) return OWLGrade.A;
        int nodePct = CMC_NODES_A > 0 ? nodes * 100 / CMC_NODES_A : 0;
        int entryPct = CMC_ENTRIES_A > 0 ? entries * 100 / CMC_ENTRIES_A : 0;
        int combined = (nodePct + entryPct) / 2;
        if (combined <= 0) return OWLGrade.T;
        if (combined < 33) return OWLGrade.D;
        return OWLGrade.P;
    }

    private static OWLGrade gradeArithmancy(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int interactions = skillData.getArithmancyInteractions(); // NEW FIELD
        return thresholdGrade(interactions, ARITH_O, ARITH_E, ARITH_A);
    }

    private static OWLGrade gradeAncientRunes(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int wandloreNodes = countTreeNodes(skillData, SkillTreeId.WANDLORE);
        int runicInteractions = skillData.getRunicInteractions(); // NEW FIELD
        int combined = wandloreNodes + runicInteractions;
        return thresholdGrade(combined, RUNES_NODES_O, RUNES_NODES_E, RUNES_NODES_A);
    }

    private static OWLGrade gradeDivination(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int events = skillData.getDivinationEvents(); // NEW FIELD
        return thresholdGrade(events, DIV_O, DIV_E, DIV_A);
    }

    private static OWLGrade gradeHistoryOfMagic(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int read = skillData.getLoreItemsRead(); // NEW FIELD
        return thresholdGrade(read, HOM_O, HOM_E, HOM_A);
    }

    private static OWLGrade gradeAstronomy(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int events = skillData.getAstronomyEvents(); // NEW FIELD
        return thresholdGrade(events, ASTRO_O, ASTRO_E, ASTRO_A);
    }

    private static OWLGrade gradeMuggleStudies(ServerPlayer player) {
        PlayerSkillData skillData = player.getData(ModAttachments.SKILL_DATA.get());
        int items = skillData.getMuggleItems(); // NEW FIELD
        return thresholdGrade(items, MUGGLE_O, MUGGLE_E, MUGGLE_A);
    }

    private static int countTreeNodes(PlayerSkillData skillData, SkillTreeId tree) {
        return (int) SkillTrees.getTree(tree).stream()
                .filter(skill -> skillData.hasSkill(skill.getId()))
                .count();
    }

    private static OWLGrade thresholdGrade(int value, int o, int e, int a) {
        if (value >= o) return OWLGrade.O;
        if (value >= e) return OWLGrade.E;
        if (value >= a) return OWLGrade.A;
        if (value <= 0) return OWLGrade.T;
        if (a > 0 && value < a / 3) return OWLGrade.D;
        return OWLGrade.P;
    }

    /** Recalculate from scratch if requested, ignoring any cached grades. */
    public static @NonNull PlayerOWLData recalculate(@NonNull ServerPlayer player) {
        Map<OWLSubject, OWLGrade> grades = calculateAll(player);
        return new PlayerOWLData(grades, true);
    }
}
