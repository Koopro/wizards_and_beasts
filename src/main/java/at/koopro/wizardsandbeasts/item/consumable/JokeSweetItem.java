package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A Weasleys' Wizard Wheezes sweet.
 *
 * <p>Eaten plainly, it does its joke to the eater. A Skiving Snackbox sweet is two-ended: eaten while
 * sneaking, the <em>other</em> end is taken — the purple half that cures what the orange half caused
 * (Order of the Phoenix, ch. 6) — and it lifts this sweet's own symptoms, and nothing else. The cure end
 * only works while the sweet's symptoms are still running, so it cannot be used as a general remedy.
 */
@NullMarked
public class JokeSweetItem extends ConsumedItem {

    private final List<Supplier<MobEffectInstance>> effects;
    private final boolean twoEnded;
    private final @Nullable ParticleOptions particle;
    private final @Nullable Supplier<SoundEvent> sound;

    public JokeSweetItem(Properties properties, List<Supplier<MobEffectInstance>> effects, boolean twoEnded,
                         @Nullable ParticleOptions particle, @Nullable Supplier<SoundEvent> sound) {
        super(properties, 16, ItemUseAnimation.EAT);
        this.effects = List.copyOf(effects);
        this.twoEnded = twoEnded;
        this.particle = particle;
        this.sound = sound;
    }

    public boolean twoEnded() {
        return twoEnded;
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        if (twoEnded && player.isShiftKeyDown()) {
            eatCureEnd(player);
        } else {
            eatJokeEnd(player);
        }
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /** The joke: this sweet's symptoms, its particles and sound, and a line saying what happened. */
    public void eatJokeEnd(Player player) {
        for (Supplier<MobEffectInstance> effect : effects) {
            player.addEffect(effect.get());
        }
        if (particle != null && player.level() instanceof ServerLevel server) {
            server.sendParticles(particle, player.getX(), player.getEyeY() - 0.2, player.getZ(),
                    20, 0.25, 0.2, 0.25, 0.05);
        }
        if (sound != null) {
            player.level().playSound(null, player.blockPosition(), sound.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        player.displayClientMessage(Component.translatable(getDescriptionId() + ".eaten")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** The purple end: lifts exactly this sweet's symptoms. */
    public void eatCureEnd(Player player) {
        boolean cured = false;
        for (Supplier<MobEffectInstance> effect : effects) {
            var holder = effect.get().getEffect();
            if (player.hasEffect(holder)) {
                player.removeEffect(holder);
                cured = true;
            }
        }
        player.displayClientMessage(Component.translatable(cured
                        ? "item.wizards_and_beasts.skiving_snackbox.cured"
                        : "item.wizards_and_beasts.skiving_snackbox.nothing_to_cure")
                .withStyle(ChatFormatting.GRAY), true);
    }
}
