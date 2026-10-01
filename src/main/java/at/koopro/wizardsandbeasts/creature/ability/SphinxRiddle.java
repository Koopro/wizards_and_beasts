package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Signature ability (Sphinx): it poses a riddle. A Sphinx guarding its ground speaks a riddle to everyone near it
 * while it has a target, on a cooldown, with a low resonant cue and enchant particles; the attack that follows is
 * its ordinary melee, which is what canon's Sphinx does to someone who answers wrongly (<i>Goblet of Fire</i>
 * ch. 31; <i>Fantastic Beasts</i>).
 *
 * <p>It used to lay Blindness and Nausea on every nearby player instead — "the disorientation of a mind caught in
 * the puzzle" — which canon never gives a Sphinx (documentation/CANON_AUDIT.md C-11). {@code duration_ticks} is
 * still read so existing datapacks load, and no longer does anything.
 */
public record SphinxRiddle(double radius, int cooldownTicks, int durationTicks) implements CreatureAbility {

    private static final String COOLDOWN_KEY = "riddle";
    /** How many riddle lines the lang file carries ({@code creature.wizards_and_beasts.sphinx.riddle.N}). */
    public static final int RIDDLES = 3;

    public static final MapCodec<SphinxRiddle> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(SphinxRiddle::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 200).forGetter(SphinxRiddle::cooldownTicks),
            Codec.INT.optionalFieldOf("duration_ticks", 120).forGetter(SphinxRiddle::durationTicks)
    ).apply(instance, SphinxRiddle::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.SPHINX_RIDDLE;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.getCooldown(COOLDOWN_KEY) > 0) {
            return;
        }
        if (entity.getTarget() == null) {
            return;
        }
        List<Player> players = AbilitySupport.nearbyPlayers(entity, radius);
        if (players.isEmpty()) {
            return;
        }
        entity.setCooldown(COOLDOWN_KEY, cooldownTicks);
        Component riddle = Component.translatable("creature.wizards_and_beasts.sphinx.riddle."
                + entity.getRandom().nextInt(RIDDLES), entity.getDisplayName()).withStyle(ChatFormatting.GOLD);
        for (Player player : players) {
            PlayerFeedback.actionBar(player, riddle);
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.ENCHANT, 16);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.HOSTILE, 1.0f, 0.7f);
    }
}
