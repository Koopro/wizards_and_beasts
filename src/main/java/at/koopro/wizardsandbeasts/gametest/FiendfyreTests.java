package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.spell.FiendfyreEntity;
import at.koopro.wizardsandbeasts.item.darkartefact.HorcruxDestruction;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuning;
import at.koopro.wizardsandbeasts.spell.tuning.SpellTuningSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * B3 (systems audit 2026-11-25): Fiendfyre is the third canon means of ending a Horcrux (DH ch.31), and nothing
 * ordinary is.
 */
public final class FiendfyreTests {

    private FiendfyreTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("fiendfyre_ends_a_horcrux_ordinary_fire_does_not",
                "fiendfyre: the diadem burns in it (DH ch.31); fire and lava leave a vessel whole",
                FiendfyreTests::endsAHorcrux);
        tests.add("fiendfyre_burns_ground_only_under_the_block_rule",
                "fiendfyre: sets the ground alight through the spellBlockDamage rule, as Incendio does",
                FiendfyreTests::obeysTheBlockRule);
        tests.add("fiendfyre_turns_on_its_caster_when_control_fails",
                "fiendfyre: a failed control roll turns the fire on the nearest living thing, caster included (Crabbe)",
                FiendfyreTests::turnsOnItsCaster);
    }

    private static void endsAHorcrux(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Spell fiendfyre = Spells.byId("fiendfyre");
        check(helper, fiendfyre != null, () -> "fiendfyre is not registered");

        Vec3 burning = helper.absoluteVec(new Vec3(2.5, 6, 2.5));
        ItemEntity diadem = new ItemEntity(level, burning.x, burning.y, burning.z,
                new ItemStack(DarkArtefactItemRegistry.RAVENCLAWS_DIADEM.get()));
        diadem.setNoGravity(true);
        level.addFreshEntity(diadem);

        Vec3 ordinary = helper.absoluteVec(new Vec3(12.5, 6, 2.5));
        ItemEntity cup = new ItemEntity(level, ordinary.x, ordinary.y, ordinary.z,
                new ItemStack(DarkArtefactItemRegistry.HUFFLEPUFFS_CUP.get()));
        cup.setNoGravity(true);
        level.addFreshEntity(cup);

        FiendfyreEntity fire = FiendfyreEntity.loose(level, burning.subtract(0, 0.5, 0), null, 1.0);
        try {
            cup.igniteForSeconds(10);
            cup.hurtServer(level, level.damageSources().inFire(), 10.0f);
            cup.hurtServer(level, level.damageSources().lava(), 10.0f);
            check(helper, cup.isAlive() && HorcruxDestruction.isIntact(cup.getItem()),
                    () -> "ordinary fire or lava destroyed a Horcrux");

            check(helper, HorcruxDestruction.isIntact(diadem.getItem()), () -> "setup: the diadem is not a Horcrux");
            fire.endHorcruxes(level);
            check(helper, !HorcruxDestruction.isIntact(diadem.getItem()), () -> "Fiendfyre did not end the diadem");
            check(helper, HorcruxDestruction.isIntact(cup.getItem()), () -> "the fire reached a cup ten blocks away");
            helper.succeed();
        } finally {
            fire.discard();
            diadem.discard();
            cup.discard();
        }
    }

    private static void obeysTheBlockRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int y = 9;
        for (int dx = 0; dx <= 6; dx++) {
            for (int dz = 0; dz <= 6; dz++) {
                helper.setBlock(new BlockPos(dx, y, dz), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(dx, y + 1, dz), Blocks.AIR);
                helper.setBlock(new BlockPos(dx, y + 2, dz), Blocks.AIR);
            }
        }
        FiendfyreEntity fire = FiendfyreEntity.loose(level, helper.absoluteVec(new Vec3(3.5, y + 1, 3.5)), null, 1.0);
        SpellTuningSnapshot.Globals before = SpellTuning.local().globals();
        try {
            SpellTuning.publishLocalGlobals(new SpellTuningSnapshot.Globals(before.damageMultiplier(),
                    before.cooldownMultiplier(), before.rangeMultiplier(), before.allowUnforgivables(), false));
            int placedOff = fire.burnGround(level, 60);
            check(helper, placedOff == 0, () -> "with spellBlockDamage off the fire set " + placedOff + " blocks alight");

            SpellTuning.publishLocalGlobals(new SpellTuningSnapshot.Globals(before.damageMultiplier(),
                    before.cooldownMultiplier(), before.rangeMultiplier(), before.allowUnforgivables(), true));
            int placedOn = fire.burnGround(level, 60);
            check(helper, placedOn > 0, () -> "with spellBlockDamage on the fire set nothing alight");
            helper.succeed();
        } finally {
            SpellTuning.publishLocalGlobals(before);
            fire.discard();
            // Put it out the same tick so nothing spreads into a neighbour's scenario.
            for (int dx = 0; dx <= 6; dx++) {
                for (int dz = 0; dz <= 6; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos p = new BlockPos(dx, y + dy, dz);
                        if (dy > 0 || level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_PLANKS)
                                || level.getBlockState(helper.absolutePos(p)).getBlock() instanceof BaseFireBlock) {
                            helper.setBlock(p, Blocks.AIR);
                        }
                    }
                }
            }
        }
    }

    private static void turnsOnItsCaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "FiendfyreCaster", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, caster);
        var cow = helper.spawn(EntityType.COW, new BlockPos(9, 1, 0));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        Vec3 at = caster.position().add(1.5, 0, 0);
        FiendfyreEntity held = FiendfyreEntity.loose(level, at, caster, 1.0);
        FiendfyreEntity lost = FiendfyreEntity.loose(level, at, caster, 0.0);
        helper.runAfterDelay(25, () -> {
            try {
                check(helper, !held.isTurned(), () -> "a fire with certain control turned");
                check(helper, lost.isTurned(), () -> "a fire with no control stayed with its caster");
                check(helper, lost.hunted() == caster,
                        () -> "the lost fire went for " + (lost.hunted() == null ? "nothing" : lost.hunted().getType())
                                + ", not the caster beside it");
                helper.succeed();
            } finally {
                held.discard();
                lost.discard();
                cow.discard();
                WizardTestSupport.retire(helper, caster);
            }
        });
    }
}
