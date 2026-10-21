package at.koopro.wizardsandbeasts.skill.build.capstone;

import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Duelist capstone, {@code duelling_riposte}: the moment after a block is the opening.
 *
 * <p>When the player's own Shield Charm turns aside a spell or a projectile, every attack spell they have on
 * cooldown comes off it. Melee absorbed by the ward does not count — the counter-jinx answers a spell, and a ward
 * soaking sword blows is not a duel. An internal cooldown ({@link #INTERNAL_COOLDOWN_TICKS}) keeps a hail of
 * arrows from turning it into "no cooldowns while shielded".
 *
 * <p>Wired at {@code ProtegoShieldEntity.trainCasterReflexes}, the one point every spell and projectile block
 * already funnels through.
 */
@NullMarked
public final class CounterJinx {

    public static final String ABILITY = "duelling_riposte";
    /** Five seconds between counters. */
    public static final long INTERNAL_COOLDOWN_TICKS = 100L;

    private static final PlayerScopedState<Long> LAST_COUNTER = PlayerScopedState.create("duelling_riposte_last");

    private CounterJinx() {}

    /** Called for every spell or projectile the caster's own ward blocks. */
    public static void onWardBlock(ServerPlayer caster) {
        if (!SkillSystemAPI.hasAbility(caster, ABILITY)) {
            return;
        }
        long now = caster.level().getGameTime();
        Long last = LAST_COUNTER.get(caster);
        if (!ready(last, now)) {
            return;
        }
        PlayerSpellData data = caster.getData(ModAttachments.SPELL_DATA.get());
        List<String> readied = readied(data.getCooldowns(), now, CounterJinx::categoryOf);
        LAST_COUNTER.put(caster, now);
        if (readied.isEmpty()) {
            return;
        }
        for (String spellId : readied) {
            data.setCooldown(spellId, now);
            SpellDataDeltaS2CPayload.sendTo(caster, spellId, now,
                    data.getCastCount(spellId), data.getSuccessfulHits(spellId), data.getGlobalCooldownEndTick());
        }
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** Whether the internal cooldown has run out. */
    public static boolean ready(Long lastCounterTick, long now) {
        return lastCounterTick == null || now - lastCounterTick >= INTERNAL_COOLDOWN_TICKS;
    }

    /** Attack spells still cooling down at {@code now}: the ones the counter readies. */
    public static List<String> readied(Map<String, Long> cooldowns, long now,
                                       Function<String, SpellCategory> category) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            if (entry.getValue() > now && category.apply(entry.getKey()) == SpellCategory.COMBAT) {
                out.add(entry.getKey());
            }
        }
        out.sort(null);
        return out;
    }

    private static SpellCategory categoryOf(String spellId) {
        Spell spell = Spells.byId(spellId);
        return spell == null ? SpellCategory.UTILITY : spell.getCategory();
    }
}
