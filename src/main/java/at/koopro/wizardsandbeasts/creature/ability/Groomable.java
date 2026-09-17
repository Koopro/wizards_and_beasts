package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * A creature that gives up what wizards want from it to someone it lets touch it: a unicorn allows a quiet,
 * empty-handed, clear-consciened visitor close enough to comb loose a few hairs; a demiguise caught unforeseen gives up a
 * tuft. Reaching it is the whole of the challenge; the interaction itself is gentle, costs the creature nothing, and
 * is the creature's signature moment in its bestiary page.
 *
 * <p>{@code require_trust} applies the wary creature's rule ({@code WildlifeRules.letsNear}); a creature whose trust is
 * earned some other way — the demiguise's, by being unpredictable — leaves it off.
 */
public record Groomable(Identifier item, int count, int cooldownTicks, boolean requireTrust, boolean puritySensitive,
                        String slayerFlag, String refusalKey) implements CreatureAbility {

    private static final String COOLDOWN = "groomed";

    public static final MapCodec<Groomable> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(Groomable::item),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Groomable::count),
            Codec.INT.optionalFieldOf("cooldown_ticks", 24000).forGetter(Groomable::cooldownTicks),
            Codec.BOOL.optionalFieldOf("require_trust", true).forGetter(Groomable::requireTrust),
            Codec.BOOL.optionalFieldOf("purity_sensitive", false).forGetter(Groomable::puritySensitive),
            Codec.STRING.optionalFieldOf("slayer_flag", "").forGetter(Groomable::slayerFlag),
            Codec.STRING.optionalFieldOf("refusal", "creature.wizards_and_beasts.groom.refused")
                    .forGetter(Groomable::refusalKey)
    ).apply(instance, Groomable::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.GROOMABLE;
    }

    @Override
    public @NonNull InteractionResult onInteract(@NonNull GenericBeastEntity entity, @NonNull Player player,
                                                 @NonNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()
                || !(player instanceof ServerPlayer serverPlayer) || !(entity.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        if (requireTrust && !WildlifeWorld.letsNear(player, entity.getType(), puritySensitive, slayerFlag)) {
            serverPlayer.displayClientMessage(Component.translatable(refusalKey, entity.getDisplayName()), true);
            return InteractionResult.CONSUME;
        }
        if (entity.getCooldown(COOLDOWN) > 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("creature.wizards_and_beasts.groom.nothing", entity.getDisplayName()), true);
            return InteractionResult.SUCCESS;
        }
        Item resolved = BuiltInRegistries.ITEM.getValue(item);
        ItemStack gift = new ItemStack(resolved, count);
        if (!serverPlayer.getInventory().add(gift)) {
            serverPlayer.drop(gift, false);
        }
        entity.setCooldown(COOLDOWN, cooldownTicks);
        level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ(),
                3, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL,
                0.6f, 1.4f);
        BestiaryDiscoveryHandler.studied(serverPlayer, entity);
        BestiaryDiscoveryHandler.witnessedSignature(serverPlayer, entity);
        return InteractionResult.SUCCESS;
    }
}
