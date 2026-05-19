package at.koopro.wizardsandbeasts.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LumosFieldEffect extends MobEffect {

    private record LightEntry(ServerLevel level, BlockPos pos) {}
    private static final Map<UUID, LightEntry> activeLights = new HashMap<>();

    public LumosFieldEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFF8E7);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player) placeOrUpdateLight(player);
    }

    public static void placeOrUpdateLight(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos target = BlockPos.containing(player.getEyePosition());
        LightEntry current = activeLights.get(player.getUUID());
        if (current != null && current.level() == level && current.pos().equals(target)) return;
        if (current != null && current.level().getBlockState(current.pos()).is(Blocks.LIGHT)) {
            current.level().removeBlock(current.pos(), false);
        }
        if (level.getBlockState(target).isAir()) {
            level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
            activeLights.put(player.getUUID(), new LightEntry(level, target));
        } else {
            activeLights.remove(player.getUUID());
        }
    }

    public static void removeLight(ServerPlayer player) {
        LightEntry entry = activeLights.remove(player.getUUID());
        if (entry != null && entry.level().getBlockState(entry.pos()).is(Blocks.LIGHT)) {
            entry.level().removeBlock(entry.pos(), false);
        }
    }
}
