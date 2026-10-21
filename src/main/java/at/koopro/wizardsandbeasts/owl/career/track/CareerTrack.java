package at.koopro.wizardsandbeasts.owl.career.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One career's long-term progression, loaded from {@code data/<ns>/career_tracks/<profession>.json}.
 *
 * <p>The career itself — who may choose it, its daily work and wage, its papers — stays where it was
 * ({@code Profession}, {@code ProfessionEligibility}, {@code CareerProfile}). A track adds what comes after the
 * choice: ranks earned with career experience <em>and</em> milestones, experience paid for the work the career
 * is about, and a board of contracts. A career with no track file keeps its flat daily wage and nothing else
 * changes for it.
 *
 * @param ranks      in order; the first is held on choosing the career and must ask for 0 experience
 * @param xpRules    experience for activity, first matching rule wins
 * @param contracts  the board's pool
 * @param maxActive  contracts a holder may carry at once
 * @param boardSize  contracts offered per in-game day
 */
@NullMarked
public record CareerTrack(List<Rank> ranks, List<XpRule> xpRules, List<ContractDefinition> contracts,
                          int maxActive, int boardSize) {

    public static final Codec<CareerTrack> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rank.CODEC.listOf(1, 32).fieldOf("ranks").forGetter(CareerTrack::ranks),
            XpRule.CODEC.listOf().optionalFieldOf("xp_rules", List.of()).forGetter(CareerTrack::xpRules),
            ContractDefinition.CODEC.listOf().optionalFieldOf("contracts", List.of()).forGetter(CareerTrack::contracts),
            Codec.intRange(1, 8).optionalFieldOf("max_active", 2).forGetter(CareerTrack::maxActive),
            Codec.intRange(1, 12).optionalFieldOf("board_size", 3).forGetter(CareerTrack::boardSize)
    ).apply(i, CareerTrack::new));

    public CareerTrack {
        ranks = List.copyOf(ranks);
        xpRules = List.copyOf(xpRules);
        contracts = List.copyOf(contracts);
    }

    /**
     * One rank.
     *
     * @param id             lang key part ({@code career.wizards_and_beasts.<profession>.rank.<id>})
     * @param xp             career experience required, cumulative
     * @param requires       milestones that must also be met
     * @param wageMultiplier scales the career's daily wage while held
     * @param rewards        handed over once, on promotion
     */
    /**
     * @param ministryRank the Ministry position reaching this rank confers, if any — an Auror track's
     *                     {@code auror} rank makes its holder an Auror in the Ministry's own records
     */
    public record Rank(String id, int xp, List<CareerGoal> requires, float wageMultiplier, List<ItemStack> rewards,
                       java.util.Optional<at.koopro.wizardsandbeasts.ministry.data.MinistryRank> ministryRank) {
        public static final Codec<Rank> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Rank::id),
                Codec.intRange(0, 1_000_000).fieldOf("xp").forGetter(Rank::xp),
                CareerGoal.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Rank::requires),
                Codec.floatRange(0.0f, 10.0f).optionalFieldOf("wage_multiplier", 1.0f).forGetter(Rank::wageMultiplier),
                ItemStack.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Rank::rewards),
                at.koopro.wizardsandbeasts.ministry.data.MinistryRank.CODEC.optionalFieldOf("ministry_rank")
                        .forGetter(Rank::ministryRank)
        ).apply(i, Rank::new));

        public Rank {
            requires = List.copyOf(requires);
            rewards = List.copyOf(rewards);
        }

        public Rank(String id, int xp, List<CareerGoal> requires, float wageMultiplier, List<ItemStack> rewards) {
            this(id, xp, requires, wageMultiplier, rewards, java.util.Optional.empty());
        }
    }

    /**
     * Experience for one kind of work.
     *
     * @param when     which activity
     * @param xp       paid per occurrence
     * @param dailyCap most this rule pays in one in-game day; 0 for no cap
     */
    public record XpRule(ActivityFilter when, int xp, int dailyCap) {
        public static final Codec<XpRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                ActivityFilter.CODEC.fieldOf("when").forGetter(XpRule::when),
                Codec.intRange(1, 10_000).fieldOf("xp").forGetter(XpRule::xp),
                Codec.intRange(0, 100_000).optionalFieldOf("daily_cap", 0).forGetter(XpRule::dailyCap)
        ).apply(i, XpRule::new));
    }

    public int rankIndex(String id) {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public @Nullable ContractDefinition contract(String id) {
        for (ContractDefinition contract : contracts) {
            if (contract.id().equals(id)) {
                return contract;
            }
        }
        return null;
    }

    /**
     * Problems a loaded track has that its codec cannot see; empty when it is sound. The loader refuses a
     * track with any, so a typo in a rank id is a load error rather than a contract no one is ever offered.
     */
    public List<String> validate() {
        java.util.ArrayList<String> problems = new java.util.ArrayList<>();
        if (ranks.getFirst().xp() != 0) {
            problems.add("first rank must require 0 xp");
        }
        for (int r = 1; r < ranks.size(); r++) {
            if (ranks.get(r).xp() <= ranks.get(r - 1).xp()) {
                problems.add("rank " + ranks.get(r).id() + " must require more xp than " + ranks.get(r - 1).id());
            }
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Rank rank : ranks) {
            if (!ids.add(rank.id())) {
                problems.add("duplicate rank " + rank.id());
            }
        }
        java.util.Set<String> contractIds = new java.util.HashSet<>();
        for (ContractDefinition contract : contracts) {
            if (!contractIds.add(contract.id())) {
                problems.add("duplicate contract " + contract.id());
            }
            if (rankIndex(contract.minRank()) < 0) {
                problems.add("contract " + contract.id() + " names unknown rank " + contract.minRank());
            }
        }
        return problems;
    }
}
