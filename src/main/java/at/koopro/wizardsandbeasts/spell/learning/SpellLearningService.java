package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.StatEffects;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SpellLearningService {

    private SpellLearningService() {
    }

    public static List<SpellOffer> buildOffers(ServerPlayer player) {
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        return buildOffers(player.getData(ModAttachments.SPELL_DATA.get()), type, tuitionFor(player));
    }

    public static List<SpellOffer> buildOffers(PlayerSpellData data) {
        return buildOffers(data, null);
    }

    public static List<SpellOffer> buildOffers(PlayerSpellData data, Heritage type) {
        return buildOffers(data, type, Config.spellTeacherLearnCostKnuts);
    }

    /**
     * @param costKnuts what a lesson costs this particular customer — see {@link #tuitionFor}. Passed
     *                  in rather than read here so the two no-player overloads above stay usable from
     *                  the unit tests, which have no {@code ServerPlayer} to derive KNOWLEDGE from.
     */
    private static List<SpellOffer> buildOffers(PlayerSpellData data, Heritage type, int costKnuts) {
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
                    costKnuts));
        }

        offers.sort(Comparator.comparing(SpellOffer::category).thenComparing(SpellOffer::displayName));
        return offers;
    }

    /**
     * What a lesson costs this player after the KNOWLEDGE discount.
     *
     * <p>The one gameplay consequence KNOWLEDGE has. It is quoted on the offer card and charged at
     * the till from this single method, so the two cannot disagree — see
     * {@link StatEffects#tuitionCost}.
     */
    public static int tuitionFor(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.PLAYER_STATS)) {
            return Config.spellTeacherLearnCostKnuts;
        }
        return StatEffects.tuitionCost(Config.spellTeacherLearnCostKnuts,
                PlayerStatsAPI.getStat(player, PlayerStat.KNOWLEDGE));
    }

    public static LearnResult tryLearnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        LearnResult validation = validateLearnAttempt(player, spell, data, type);
        if (!validation.success()) {
            return validation;
        }

        int fee = tuitionFor(player);
        if (Config.spellTeacherRequirePayment && fee > 0) {
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            long withdrawn = vault.withdrawSmartKnuts(fee);
            if (withdrawn < fee) {
                if (withdrawn > 0) {
                    vault.depositKnuts(withdrawn);
                }
                long[] price = CurrencyHelper.fromKnuts(fee);
                return LearnResult.failure("Not enough in your vault — this lesson costs "
                        + CurrencyHelper.formatCurrency(price[0], price[1], price[2]) + ".");
            }
        }

        data.learnSpell(spell.getId());
        SpellDataSyncS2CPayload.syncToPlayer(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from spells learned
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
