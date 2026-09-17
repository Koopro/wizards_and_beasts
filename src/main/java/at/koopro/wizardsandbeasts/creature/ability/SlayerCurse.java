package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Killing this creature marks the killer. "It is a monstrous thing, to slay a unicorn" (<i>Philosopher's Stone</i>): the
 * killer is remembered by every creature that can sense such things ({@code flag}, read by {@link Wary} and
 * {@link Groomable}), their soul is darkened by {@code corruption}, and they carry {@code effects} for a time.
 *
 * <p>Only a kill made by a player's own hand counts; a unicorn taken by a wolf curses nobody.
 */
public record SlayerCurse(String flag, float corruption, List<AbilitySupport.EffectSpec> effects, String messageKey)
        implements CreatureAbility {

    /** A blow landed this recently is the one that killed. */
    private static final int KILLING_BLOW_TICKS = 100;

    public static final MapCodec<SlayerCurse> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("flag").forGetter(SlayerCurse::flag),
            Codec.FLOAT.optionalFieldOf("corruption", 10.0f).forGetter(SlayerCurse::corruption),
            AbilitySupport.EffectSpec.LIST_CODEC.optionalFieldOf("effects", List.of()).forGetter(SlayerCurse::effects),
            Codec.STRING.optionalFieldOf("message", "creature.wizards_and_beasts.slayer.cursed")
                    .forGetter(SlayerCurse::messageKey)
    ).apply(instance, SlayerCurse::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.SLAYER_CURSE;
    }

    @Override
    public void onDeath(@NonNull GenericBeastEntity entity) {
        if (!(entity.getLastHurtByMob() instanceof ServerPlayer killer)
                || entity.tickCount - entity.getLastHurtByMobTimestamp() > KILLING_BLOW_TICKS) {
            return;
        }
        PlayerAbilityHelper.addAbilityFlag(killer, flag);
        if (corruption > 0.0f) {
            DarkCorruptionService.accrue(killer, corruption);
        }
        AbilitySupport.applyAll(killer, effects);
        killer.sendSystemMessage(Component.translatable(messageKey, entity.getDisplayName())
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
