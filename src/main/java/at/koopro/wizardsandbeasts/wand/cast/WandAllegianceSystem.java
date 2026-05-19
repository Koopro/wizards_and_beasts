package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class WandAllegianceSystem {
    private WandAllegianceSystem() {}

    public static WandAllegiance resolve(ItemStack wandStack) {
        return WandCastingAllegianceSystem.resolve(wandStack);
    }

    public static Compatibility.Score applyLayer(CastContext ctx, ServerLevel level) {
        return WandCastingAllegianceSystem.applyLayer(ctx, level);
    }

    public static void onSuccessfulCast(ServerPlayer caster, ItemStack wandStack, ServerLevel level) {
        WandCastingAllegianceSystem.onSuccessfulCast(caster, wandStack, level);
    }

    public static void transferTo(ItemStack wandStack, UUID newOwner, float bondCarryRatio, long gameTick) {
        WandCastingAllegianceSystem.transferTo(wandStack, newOwner, bondCarryRatio, gameTick);
    }
}
