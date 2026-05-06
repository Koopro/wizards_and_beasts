package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.data.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.SpellDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.Spells;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import at.koopro.wizardsandbeasts.type.Heritage;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpellLearningService {

    private SpellLearningService() {
    }

    public static List<SpellOffer> buildOffers(ServerPlayer player) {
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        return buildOffers(player.getData(ModAttachments.SPELL_DATA.get()), type);
    }

    public static List<SpellOffer> buildOffers(PlayerSpellData data) {
        return buildOffers(data, null);
    }

    public static List<SpellOffer> buildOffers(PlayerSpellData data, Heritage type) {
        List<SpellOffer> offers = new ArrayList<>();

        for (Spell spell : Spells.all()) {
            if (ObscurialRules.isObscurialAbility(spell)) {
                continue;
            }
            if (data.knowsSpell(spell.getId())) {
                continue;
            }
            boolean typeEligible = ObscurialRules.canHeritageUseSpell(type, spell);
            boolean requirementMet = typeEligible && isLearnable(spell, data);
            String requirementText = requirementMet
                    ? ""
                    : (typeEligible ? spell.getRequirement().getDescription() : "Only Obscurials can learn this spell.");
            offers.add(new SpellOffer(
                    spell.getId(),
                    spell.getDisplayName(),
                    spell.getCategory().name(),
                    requirementMet,
                    requirementText,
                    Config.spellTeacherLearnCostKnuts));
        }

        offers.sort(Comparator.comparing(SpellOffer::category).thenComparing(SpellOffer::displayName));
        return offers;
    }

    public static LearnResult tryLearnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        LearnResult validation = validateLearnAttempt(spell, data, type);
        if (!validation.success()) {
            return validation;
        }

        if (Config.spellTeacherRequirePayment && Config.spellTeacherLearnCostKnuts > 0) {
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            long withdrawn = vault.withdrawSmartKnuts(Config.spellTeacherLearnCostKnuts);
            if (withdrawn < Config.spellTeacherLearnCostKnuts) {
                if (withdrawn > 0) {
                    vault.depositKnuts(withdrawn);
                }
                return LearnResult.failure("Not enough vault funds.");
            }
        }

        data.learnSpell(spell.getId());
        SpellDataSyncS2CPacket.syncToPlayer(player);
        return LearnResult.success(spell.getDisplayName());
    }

    public static LearnResult validateLearnAttempt(Spell spell, PlayerSpellData data) {
        return validateLearnAttempt(spell, data, null);
    }

    public static LearnResult validateLearnAttempt(Spell spell, PlayerSpellData data, Heritage type) {
        if (spell == null) {
            return LearnResult.failure("Unknown spell.");
        }
        if (ObscurialRules.isObscurialAbility(spell)) {
            return LearnResult.failure("This is an Obscurial ability, not a learnable spell.");
        }
        if (!ObscurialRules.canHeritageUseSpell(type, spell)) {
            return LearnResult.failure("Only Obscurials can learn this spell.");
        }
        if (data.knowsSpell(spell.getId())) {
            return LearnResult.failure("You already know this spell.");
        }
        if (!isLearnable(spell, data)) {
            return LearnResult.failure(spell.getRequirement().getDescription());
        }
        return LearnResult.success("ok");
    }

    public static boolean isLearnable(Spell spell, PlayerSpellData data) {
        return spell.getRequirement().isMet(data);
    }

    public record SpellOffer(
            String spellId,
            String displayName,
            String category,
            boolean learnable,
            String requirementText,
            int costKnuts) {
    }

    public record LearnResult(boolean success, String message) {
        public static LearnResult success(String message) {
            return new LearnResult(true, message);
        }

        public static LearnResult failure(String message) {
            return new LearnResult(false, message);
        }
    }
}
