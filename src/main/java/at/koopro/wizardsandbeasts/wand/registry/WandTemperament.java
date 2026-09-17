package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How a wand component behaves in its relationship with a wielder, as opposed to what it adds to a spell
 * ({@link WandCastModifiers}).
 *
 * <p>Every field is a <em>modifier relative to the baseline</em> in {@code WandAllegianceRules}, and every
 * default is the identity, so a definition that says nothing behaves like an ordinary wand. A wand's
 * temperament is its wood's and its core's {@linkplain #combine combined}: multipliers multiply, additive
 * fields add, flags are either-or.
 *
 * <p>The fields are gameplay interpretations of Garrick Ollivander's wandlore — each definition's JSON says
 * which trait it reads — not rules quoted from the books.
 *
 * @param bondGrowth               multiplier on the bond a successful cast adds
 * @param extraWins                added to the baseline number of defeats that win the wand from its master
 * @param transferBondBonus        added to the bond a wand holds for a wizard who has just won it
 * @param darkArtsBondCost         bond lost when the master casts Dark Arts with it; that cast adds none
 * @param bondNeedsDanger          the bond only deepens through casts made in danger
 * @param backfiresInForeignHands  spells backfire for anyone who is not its master
 * @param foreignHandPower         multiplier on the power it lends a wizard who is not its master
 * @param passedOnPower            multiplier on its power once it serves anyone but its first master
 * @param masteryNeedsDeathWitness it cannot be mastered by a wizard who has not seen death
 * @param loyalCooldown            cooldown multiplier once it is loyal to its master
 */
public record WandTemperament(
        float bondGrowth,
        int extraWins,
        float transferBondBonus,
        float darkArtsBondCost,
        boolean bondNeedsDanger,
        boolean backfiresInForeignHands,
        float foreignHandPower,
        float passedOnPower,
        boolean masteryNeedsDeathWitness,
        float loyalCooldown) {

    public static final WandTemperament NEUTRAL =
            new WandTemperament(1.0f, 0, 0.0f, 0.0f, false, false, 1.0f, 1.0f, false, 1.0f);

    public static final Codec<WandTemperament> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("bond_growth", 1.0f).forGetter(WandTemperament::bondGrowth),
            Codec.INT.optionalFieldOf("extra_wins", 0).forGetter(WandTemperament::extraWins),
            Codec.FLOAT.optionalFieldOf("transfer_bond_bonus", 0.0f).forGetter(WandTemperament::transferBondBonus),
            Codec.FLOAT.optionalFieldOf("dark_arts_bond_cost", 0.0f).forGetter(WandTemperament::darkArtsBondCost),
            Codec.BOOL.optionalFieldOf("bond_needs_danger", false).forGetter(WandTemperament::bondNeedsDanger),
            Codec.BOOL.optionalFieldOf("backfires_in_foreign_hands", false)
                    .forGetter(WandTemperament::backfiresInForeignHands),
            Codec.FLOAT.optionalFieldOf("foreign_hand_power", 1.0f).forGetter(WandTemperament::foreignHandPower),
            Codec.FLOAT.optionalFieldOf("passed_on_power", 1.0f).forGetter(WandTemperament::passedOnPower),
            Codec.BOOL.optionalFieldOf("mastery_needs_death_witness", false)
                    .forGetter(WandTemperament::masteryNeedsDeathWitness),
            Codec.FLOAT.optionalFieldOf("loyal_cooldown", 1.0f).forGetter(WandTemperament::loyalCooldown)
    ).apply(instance, WandTemperament::new));

    /** Wood and core together — the wand's temperament, as one. Order does not matter. */
    public WandTemperament combine(WandTemperament other) {
        return new WandTemperament(
                bondGrowth * other.bondGrowth,
                extraWins + other.extraWins,
                transferBondBonus + other.transferBondBonus,
                darkArtsBondCost + other.darkArtsBondCost,
                bondNeedsDanger || other.bondNeedsDanger,
                backfiresInForeignHands || other.backfiresInForeignHands,
                foreignHandPower * other.foreignHandPower,
                passedOnPower * other.passedOnPower,
                masteryNeedsDeathWitness || other.masteryNeedsDeathWitness,
                loyalCooldown * other.loyalCooldown);
    }
}
