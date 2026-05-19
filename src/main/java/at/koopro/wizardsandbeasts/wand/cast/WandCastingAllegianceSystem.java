package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class WandCastingAllegianceSystem {
    private static final float BOND_GROWTH_PER_SUCCESS = 0.001f;

    private WandCastingAllegianceSystem() {}

    public static WandAllegiance resolve(ItemStack wandStack) {
        WandAllegiance value = wandStack.get(ModDataComponents.WAND_ALLEGIANCE.get());
        return value == null ? WandAllegiance.unbound() : value;
    }

    public static Compatibility.Score applyLayer(CastContext ctx, ServerLevel level) {
        if (!Config.enableWandAllegiance) {
            return new Compatibility.Score(0.5f, 0.5f);
        }
        WandAllegiance allegiance = resolve(ctx.wandStack());
        PlayerHeritageData typeData = ctx.caster().getData(ModAttachments.HERITAGE_DATA.get());
        Compatibility.Score score = Compatibility.score(ctx.wandStack(), typeData, allegiance);
        UUID casterId = ctx.caster().getUUID();

        if (allegiance.isBound() && casterId.equals(allegiance.boundPlayer())) {
            float compat = score.totalCompat();
            float damageMul = 1.0f + (compat - 0.5f) * 0.4f;
            float misfireAdd = Math.max(0f, (0.5f - compat) * 0.3f);
            ctx.modifiers().multiplyDamage(damageMul, "wand_allegiance");
            ctx.modifiers().addMisfireChance(misfireAdd, "wand_allegiance");
            return score;
        }

        if (allegiance.isBound() && !casterId.equals(allegiance.boundPlayer())) {
            ctx.modifiers().multiplyDamage(0.6f, "wand_allegiance_other");
            ctx.modifiers().addMisfireChance(0.4f, "wand_allegiance_other");
            ctx.modifiers().multiplyCooldown(1.5f, "wand_allegiance_other");
            return score;
        }

        float compat = score.initialCompat();
        float damageMul = 1.0f + (compat - 0.5f) * 0.4f;
        float misfireAdd = Math.max(0f, (0.5f - compat) * 0.3f);
        ctx.modifiers().multiplyDamage(damageMul, "wand_allegiance_unbound");
        ctx.modifiers().addMisfireChance(misfireAdd, "wand_allegiance_unbound");
        return score;
    }

    public static void onSuccessfulCast(ServerPlayer caster, ItemStack wandStack, ServerLevel level) {
        if (!Config.enableWandAllegiance || wandStack.isEmpty()) {
            return;
        }
        WandAllegiance allegiance = resolve(wandStack);
        PlayerHeritageData typeData = caster.getData(ModAttachments.HERITAGE_DATA.get());
        Compatibility.Score score = Compatibility.score(wandStack, typeData, allegiance);

        if (!allegiance.isBound()) {
            if (!Compatibility.canBind(score.initialCompat())) {
                return;
            }
            allegiance = new WandAllegiance(caster.getUUID(), 0.0f, level.getGameTime());
        }

        if (!caster.getUUID().equals(allegiance.boundPlayer())) {
            return;
        }

        float nextBond = Math.min(1.0f, allegiance.bondStrength() + BOND_GROWTH_PER_SUCCESS);
        wandStack.set(ModDataComponents.WAND_ALLEGIANCE.get(), allegiance.withBondStrength(nextBond));
    }

    public static void transferTo(ItemStack wandStack, UUID newOwner, float bondCarryRatio, long gameTick) {
        if (wandStack.isEmpty()) {
            return;
        }
        WandAllegiance current = resolve(wandStack);
        float carried = Math.max(0.0f, Math.min(1.0f, current.bondStrength() * bondCarryRatio));
        wandStack.set(
                ModDataComponents.WAND_ALLEGIANCE.get(),
                new WandAllegiance(newOwner, carried, gameTick));
    }
}
