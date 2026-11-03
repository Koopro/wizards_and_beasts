package at.koopro.wizardsandbeasts.mixin;

import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps vanilla's dungeons out of a settlement's ground.
 *
 * <h2>Why a mixin</h2>
 *
 * <p>NeoForge has no event around a feature's placement, and data cannot reach this. The tag
 * {@code features_cannot_replace} is honoured only by part of {@link MonsterRoomFeature#place}: a room's edge cell over
 * a non-solid block is set to air with no check at all, and that is what opened the Undercroft's wall. Removing the
 * feature with a biome modifier would remove every dungeon from every biome a hamlet can stand in, and replacing its
 * placed-feature file to add a filter would fight any other mod that touches it.
 *
 * <h2>Why this point</h2>
 *
 * <p>The room runs in the {@code underground_structures} step, before a hamlet's own step in the same chunk, but a chunk
 * next door may already have finished. So a room started under a hamlet writes into pieces already placed, then its own
 * half is overwritten, spawner and all, leaving a breach with no dungeon to explain it. It has to be stopped before it
 * starts, and the only thing known that early is the chunk's structure references, which are in place by then.
 */
@Mixin(MonsterRoomFeature.class)
public class MonsterRoomFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void wizards_and_beasts$notUnderASettlement(FeaturePlaceContext<NoneFeatureConfiguration> context,
                                                        CallbackInfoReturnable<Boolean> result) {
        if (SettlementGround.reserved(context.level(), context.origin())) {
            result.setReturnValue(false);
        }
    }
}
