package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.Heritage;
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
            SpellLearningEligibility.Result eligibility = SpellLearningEligibility.evaluate(null, spell, data, type);
            if (ObscurialRules.isObscurialAbility(spell) || data.knowsSpell(spell.getId())) {
                continue;
            }
            offers.add(new SpellOffer(
                    spell.getId(),
                    spell.getDisplayName(),
                    spell.getCategory().name(),
                    eligibility.learnable(),
                    eligibility.reason(),
                    Config.spellTeacherLearnCostKnuts));
        }

        offers.sort(Comparator.comparing(SpellOffer::category).thenComparing(SpellOffer::displayName));
        return offers;
    }

    public static LearnResult tryLearnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        LearnResult validation = validateLearnAttempt(player, spell, data, type);
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
        SpellDataSyncS2CPayload.syncToPlayer(player);
        return LearnResult.success(spell.getDisplayName());
    }

    public static LearnResult validateLearnAttempt(Spell spell, PlayerSpellData data) {
        return validateLearnAttempt(null, spell, data, null);
    }

    public static LearnResult validateLearnAttempt(Spell spell, PlayerSpellData data, Heritage type) {
        return validateLearnAttempt(null, spell, data, type);
    }

    public static LearnResult validateLearnAttempt(ServerPlayer player, Spell spell, PlayerSpellData data, Heritage type) {
        if (spell == null) {
            return LearnResult.failure("Unknown spell.");
        }
        SpellLearningEligibility.Result eligibility = SpellLearningEligibility.evaluate(player, spell, data, type);
        if (!eligibility.learnable()) {
            return LearnResult.failure(eligibility.reason());
        }
        return LearnResult.success("ok");
    }

    public static boolean isLearnable(Spell spell, PlayerSpellData data) {
        return SpellLearningEligibility.evaluate(null, spell, data, null).learnable();
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
