package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

final class SpellCastSupport {
    private SpellCastSupport() {
    }

    static int lockTier(BlockState state) {
        if (state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR)) return 2;
        if (state.getBlock() instanceof DoorBlock) return 1;
        if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) return 1;
        return 0;
    }

    static boolean canUnlockTier(Proficiency proficiency, int tier) {
        return switch (tier) {
            case 0 -> true;
            case 1 -> proficiency == Proficiency.PROFICIENT || proficiency == Proficiency.MASTERED;
            default -> proficiency == Proficiency.MASTERED;
        };
    }

    static boolean isAccio(Spell spell) { return SpellIds.matches(spell.getId(), "accio"); }
    static boolean isGlacius(Spell spell) { return SpellIds.matches(spell.getId(), "glacius"); }
    static boolean isImperio(Spell spell) { return SpellIds.matches(spell.getId(), "imperio"); }
    static boolean isBombarda(Spell spell) { return SpellIds.matches(spell.getId(), "bombarda"); }
    static boolean isExpectoPatronum(Spell spell) { return SpellIds.matches(spell.getId(), "expecto_patronum"); }
    static boolean isColloportus(Spell spell) { return SpellIds.matches(spell.getId(), "colloportus"); }
    static boolean isLiberacorpus(Spell spell) { return SpellIds.matches(spell.getId(), "liberacorpus"); }
    static boolean isLevicorpus(Spell spell) { return SpellIds.matches(spell.getId(), "levicorpus"); }

    static boolean isFiniteIncantatem(Spell spell) {
        String id = spell.getId();
        if (SpellIds.matches(id, "finite_incantatem")) {
            return true;
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.equals("finite_incantatem") || path.endsWith("/finite_incantatem");
    }

    static boolean isPatronusDarkAligned(LivingEntity entity) {
        if (entity.isInvertedHealAndHarm()) {
            return true;
        }
        if (entity instanceof ServerPlayer sp) {
            PlayerHeritageData data = sp.getData(ModAttachments.HERITAGE_DATA.get());
            return ObscurialRules.isObscurial(data) && ObscurialRules.isDarkForm(data);
        }
        return false;
    }
}
