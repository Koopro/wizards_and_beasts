package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

import java.util.Map;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The curated spells of Phase 7: each is written (no longer "coming soon") and does to what it hits what its name
 * says -- run through the same effect components a cast's impact runs.
 */
public final class CuratedSpellTests {

    /** Spell -> the mob effect its impact must leave on the target. */
    private static final Map<String, String> EFFECT_OF = Map.of(
            "petrificus_totalus", "petrificus_totalus",
            "impedimenta", "impedimenta",
            "tarantallegra", "tarantallegra",
            "confundo", "confundo",
            "langlock", "langlock",
            "engorgio", "engorgio",
            "reducio", "reducio",
            "obscuro", "obscuro",
            "furnunculus", "furnunculus");

    private CuratedSpellTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("curated_spells_do_what_they_say",
                "spells: Petrificus Totalus, Impedimenta, Tarantallegra, Confundo, Langlock, Engorgio, Reducio, "
                        + "Obscuro and Furnunculus are written and leave their effect on what they hit; Expulso blasts it",
                CuratedSpellTests::curatedSpells);
    }

    private static void curatedSpells(GameTestHelper helper) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "CuratedCaster", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, caster);
        try {
            for (Map.Entry<String, String> entry : EFFECT_OF.entrySet()) {
                Spell spell = Spells.byId("wizards_and_beasts:" + entry.getKey());
                check(helper, spell != null && spell.isImplemented(), () -> entry.getKey() + " is not written");
                Zombie target = target(helper);
                SpellEffectRunner.run(spell, SpellEffectContext.ofTarget(caster, target, helper.getLevel()));
                var effect = BuiltInRegistries.MOB_EFFECT.get(
                        Identifier.fromNamespaceAndPath("wizards_and_beasts", entry.getValue())).orElseThrow();
                check(helper, target.hasEffect(effect), () -> entry.getKey() + " left no " + entry.getValue());
                target.discard();
            }

            Spell expulso = Spells.byId("wizards_and_beasts:expulso");
            check(helper, expulso != null && expulso.isImplemented(), () -> "expulso is not written");
            Zombie target = target(helper);
            float before = target.getHealth();
            SpellEffectRunner.run(expulso, SpellEffectContext.ofTarget(caster, target, helper.getLevel()));
            check(helper, target.getHealth() < before || target.getDeltaMovement().lengthSqr() > 0.01,
                    () -> "Expulso neither hurt nor moved what it hit");
            target.discard();
            helper.succeed();
        } finally {
            retire(helper, caster);
        }
    }

    private static Zombie target(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        return zombie;
    }
}
