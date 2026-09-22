package at.koopro.wizardsandbeasts.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LumosFieldEffect extends MobEffect {

    /** One light at the wand, plus however many the caster's training carries ahead of them. */
    private record LightEntry(ServerLevel level, List<BlockPos> positions) {}

    private static final Map<UUID, LightEntry> activeLights = new HashMap<>();

    /** A trained Lumos cannot light the whole world; four steps ahead is a corridor, not a floodlight. */
    private static final int MAX_REACH = 4;

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

    /**
     * Puts the wand-light where the caster is looking from, and — for a caster who has trained the charm —
     * another light every block further along their gaze.
     *
     * <p>{@link GameplayStat#LIGHT_REACH} is the whole Lumos progression made real: the first node lights
     * your own hand, the last one lights the corridor in front of you. Every light is placed only into air
     * and taken back the moment the caster moves, so a walked corridor leaves nothing behind.
     */
    public static void placeOrUpdateLight(ServerPlayer player) {
        ServerLevel level = player.level();
        List<BlockPos> wanted = wantedPositions(player, level);
        LightEntry current = activeLights.get(player.getUUID());
        if (current != null && current.level() == level && current.positions().equals(wanted)) {
            return;
        }
        clear(current);
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : wanted) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                placed.add(pos);
            }
        }
        if (placed.isEmpty()) {
            activeLights.remove(player.getUUID());
        } else {
            activeLights.put(player.getUUID(), new LightEntry(level, placed));
        }
    }

    /** The eye block, then one block per trained step along the look vector. */
    private static List<BlockPos> wantedPositions(ServerPlayer player, ServerLevel level) {
        int reach = Math.min(MAX_REACH, Math.round(
                SkillSystemAPI.getGameplayBonus(player, GameplayStat.LIGHT_REACH)));
        List<BlockPos> positions = new ArrayList<>();
        positions.add(BlockPos.containing(player.getEyePosition()));
        if (reach <= 0) {
            return positions;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (int step = 1; step <= reach; step++) {
            BlockPos ahead = BlockPos.containing(eye.add(look.scale(step)));
            if (!positions.contains(ahead) && level.getBlockState(ahead).isAir()) {
                positions.add(ahead);
            }
        }
        return positions;
    }

    public static void removeLight(ServerPlayer player) {
        clear(activeLights.remove(player.getUUID()));
    }

    private static void clear(@Nullable LightEntry entry) {
        if (entry == null) {
            return;
        }
        for (BlockPos pos : entry.positions()) {
            if (entry.level().getBlockState(pos).is(Blocks.LIGHT)) {
                entry.level().removeBlock(pos, false);
            }
        }
    }
}
