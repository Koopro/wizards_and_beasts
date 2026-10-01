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
 * Signature ability (Jarvey): the overgrown ferret that talks — in "short (and often very rude) phrases"
 * (<i>Fantastic Beasts</i>). While it has a target, on a cooldown it shouts an insult at everyone nearby, with a
 * screech and dread particles.
 *
 * <p>It used to lay Unluck and Weakness on them as a "jinx", which canon never gives a Jarvey
 * (documentation/CANON_AUDIT.md C-11). The ability keeps its registered name so datapacks keep loading;
 * {@code duration_ticks} is still read and no longer does anything.
 */
public record JarveyJinx(double radius, int cooldownTicks, int durationTicks) implements CreatureAbility {

    private static final String COOLDOWN_KEY = "jarvey";
    /** How many insult lines the lang file carries ({@code creature.wizards_and_beasts.jarvey.insult.N}). */
    public static final int INSULTS = 4;

    public static final MapCodec<JarveyJinx> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(JarveyJinx::radius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 160).forGetter(JarveyJinx::cooldownTicks),
            Codec.INT.optionalFieldOf("duration_ticks", 200).forGetter(JarveyJinx::durationTicks)
    ).apply(instance, JarveyJinx::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.JARVEY_JINX;
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
        Component insult = Component.translatable("creature.wizards_and_beasts.jarvey.insult."
                + entity.getRandom().nextInt(INSULTS), entity.getDisplayName()).withStyle(ChatFormatting.RED);
        for (Player player : players) {
            PlayerFeedback.actionBar(player, insult);
        }
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.DREAD, 8);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.FOX_SCREECH, SoundSource.HOSTILE, 1.0f, 1.3f);
    }
}
