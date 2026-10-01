package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Centaurs keep their forest and are nobody's mount.
 *
 * <ul>
 *   <li><b>The forest:</b> felling a tree within {@link SignatureRules#CENTAUR_FOREST_RADIUS} of a centaur is harm
 *       done to the herd. The first time, it warns the woodcutter off; doing it again while the warning stands turns
 *       it on them (with {@code PACK}, the herd joins). Wired from the block-break event by
 *       {@code CentaurForestWatch}.</li>
 *   <li><b>No rider:</b> a centaur will not carry a human. Asking — reaching for it with an empty hand — gets the
 *       answer Bane gave Firenze: they are not beasts of burden.</li>
 * </ul>
 */
public record ForestKeeper(int grudgeTicks) implements CreatureAbility {

    static final String WARNED = "forest_warned:";

    public static final MapCodec<ForestKeeper> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("grudge_ticks", 1200).forGetter(ForestKeeper::grudgeTicks)
    ).apply(instance, ForestKeeper::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.FOREST_KEEPER;
    }

    @Override
    public @NonNull InteractionResult onInteract(@NonNull GenericBeastEntity entity, @NonNull Player player,
                                                 @NonNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("entity.wizards_and_beasts.centaur.no_rider"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A tree came down at {@code pos}. Every keeping centaur near it warns the woodcutter, or turns on them if already
     * warned.
     *
     * @return how many centaurs turned on the woodcutter
     */
    public static int treeFelled(ServerLevel level, BlockPos pos, Player woodcutter) {
        if (woodcutter.isCreative() || woodcutter.isSpectator()) {
            return 0;
        }
        int turned = 0;
        AABB around = new AABB(pos).inflate(SignatureRules.CENTAUR_FOREST_RADIUS);
        for (GenericBeastEntity centaur : level.getEntitiesOfClass(GenericBeastEntity.class, around,
                e -> e.isAlive() && e.hasAbility(CreatureAbility.Type.FOREST_KEEPER))) {
            ForestKeeper keeper = centaur.abilityOf(ForestKeeper.class);
            if (keeper == null) {
                continue;
            }
            String key = WARNED + woodcutter.getUUID();
            if (centaur.getCooldown(key) > 0) {
                centaur.setTarget(woodcutter);
                turned++;
            } else {
                centaur.setCooldown(key, keeper.grudgeTicks());
                centaur.getLookControl().setLookAt(woodcutter);
                if (woodcutter instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("entity.wizards_and_beasts.centaur.warns_felling"), true);
                }
            }
        }
        return turned;
    }
}
