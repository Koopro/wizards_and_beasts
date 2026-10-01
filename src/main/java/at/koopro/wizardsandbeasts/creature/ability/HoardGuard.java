package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Signature ability (Griffin): it guards treasure. Wizards employ griffins to guard their gold (Fantastic Beasts); this
 * one finds gold of its own and keeps it.
 *
 * <ul>
 *   <li>With no hoard it looks for one: the nearest block tagged {@code wizards_and_beasts:griffin_hoard} within
 *       {@code search_radius}. The hoard becomes its home ({@code setHomeTo}, saved with it) and it stays by it.</li>
 *   <li>Someone who comes within {@link SignatureRules#HOARD_RADIUS} of the gold is screeched at once; staying after
 *       the warning is an attack ({@link SignatureRules#hoardCrowding}). It does not chase a thief far from its gold —
 *       target goals drop anyone outside the home.</li>
 *   <li>The one person who has earned it by feeding it raw meat — its bond owner at {@code tolerate_bond} or more — may
 *       come near. That is the whole of the friendship canon allows ("a handful of skilled wizards"): no riding, no
 *       following it away from its gold.</li>
 *   <li>Gold taken away is noticed: a hoard that is no longer there is given up, and it looks for another.</li>
 * </ul>
 */
public record HoardGuard(double searchRadius, int tolerateBond) implements CreatureAbility {

    public static final TagKey<Block> HOARD = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "griffin_hoard"));

    static final String WARNED = "hoard_warned:";
    static final String SEARCH = "hoard_search";

    public static final MapCodec<HoardGuard> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("search_radius", 10.0).forGetter(HoardGuard::searchRadius),
            Codec.INT.optionalFieldOf("tolerate_bond", 25).forGetter(HoardGuard::tolerateBond)
    ).apply(instance, HoardGuard::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.HOARD_GUARD;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(entity, 1.0) {
            @Override
            public boolean canUse() {
                return ModuleManager.isEnabled(Module.CREATURES) && entity.getTarget() == null && super.canUse();
            }
        });
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 10 != 0) {
            return;
        }
        if (entity.hasHome() && !level.getBlockState(entity.getHomePosition()).is(HOARD)) {
            entity.clearHome(); // the gold is gone
        }
        if (!entity.hasHome()) {
            if (entity.getCooldown(SEARCH) == 0) {
                entity.setCooldown(SEARCH, 100);
                BlockPos found = findHoard(level, entity.blockPosition());
                if (found != null) {
                    entity.setHomeTo(found, (int) Math.ceil(SignatureRules.HOARD_RADIUS));
                }
            }
            return;
        }
        guard(entity, level);
    }

    /** The nearest hoard block within {@code search_radius} of {@code centre}, or {@code null}. */
    public @Nullable BlockPos findHoard(ServerLevel level, BlockPos centre) {
        int r = (int) Math.ceil(searchRadius);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -4, -r), centre.offset(r, 4, r))) {
            if (level.getBlockState(pos).is(HOARD)) {
                double dist = pos.distSqr(centre);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    /** Warns off, then attacks, whoever comes near the hoard without having earned it. */
    public void guard(@NonNull GenericBeastEntity entity, ServerLevel level) {
        BlockPos hoard = entity.getHomePosition();
        for (Player player : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(hoard)
                .inflate(SignatureRules.HOARD_RADIUS + 1), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            double distance = Math.sqrt(player.distanceToSqr(hoard.getCenter()));
            String key = WARNED + player.getUUID();
            int remaining = entity.getCooldown(key);
            int sinceWarned = remaining > 0 ? SignatureRules.HOARD_WARNING_TICKS - remaining : -1;
            WildlifeRules.Crowding response = SignatureRules.hoardCrowding(tolerates(entity, player), distance, sinceWarned);
            switch (response) {
                case WARN -> {
                    entity.setCooldown(key, SignatureRules.HOARD_WARNING_TICKS);
                    entity.getLookControl().setLookAt(player);
                    entity.triggerDeclared("attack");
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.GRIFFIN_SCREECH.get(),
                            SoundSource.HOSTILE, 1.4f, 1.0f);
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.displayClientMessage(
                                Component.translatable("entity.wizards_and_beasts.griffin.warns"), true);
                        BestiaryDiscoveryHandler.witnessedSignature(serverPlayer, entity);
                    }
                }
                case ATTACK -> entity.setTarget(player);
                case NONE -> { }
            }
        }
    }

    /** The person who feeds it may come near its gold. */
    public boolean tolerates(@NonNull GenericBeastEntity entity, Player player) {
        return entity.bondState().isOwnedBy(player.getUUID()) && entity.bondState().level() >= tolerateBond;
    }
}
