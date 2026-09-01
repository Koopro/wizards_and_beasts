package at.koopro.wizardsandbeasts.shadow;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Shadow Form: half-there, quick, and badly allergic to light.
 *
 * <p>The bargain is explicit and it is what separates this from every other concealment in the mod.
 * A Demiguise hair hides you outright and costs a rare reagent. This hides you <em>partly</em> —
 * mobs still see you; you are simply harder to look at and faster than you should be — and the price
 * is paid whenever somebody points a light at you.
 *
 * <h2>Why "semi"</h2>
 * No camouflage. The Hidebehind's own trick is not shared with the wizard drinking it: you are
 * invisible to eyes, and every hostile thing in the world still knows exactly where you are. That is
 * the difference between borrowing a creature's shadow and being one.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ShadowForm {

    /**
     * Extra damage taken from light magic while in Shadow Form.
     *
     * <p>Twenty percent, as briefed. Enough that a duellist who knows what you drank has an answer,
     * small enough that it is not simply a trap for the person who drank it.
     */
    public static final float LIGHT_VULNERABILITY = 0.20f;

    /** The damage type light magic deals. Spells opt in by name, in code or in their JSON. */
    public static final ResourceKey<DamageType> LIGHT_MAGIC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "light_magic"));

    /**
     * Everything Shadow Form is vulnerable to.
     *
     * <p>A tag rather than the single damage type, so a datapack can decide that its own sunbeam or
     * a Patronus counts without this class knowing about it — and so the answer to "what is
     * light-based" lives in data, where that question belongs.
     */
    public static final TagKey<DamageType> LIGHT_MAGIC_TAG = TagKey.create(
            Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "light_magic"));

    private ShadowForm() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.hasEffect(ModEffects.SHADOW_FORM)) {
            return;
        }
        if (!isLightMagic(event.getSource())) {
            return;
        }
        event.setAmount(amplify(event.getAmount()));
    }

    /** Whether a blow counts as light magic. */
    public static boolean isLightMagic(DamageSource source) {
        return source.is(LIGHT_MAGIC_TAG);
    }

    /**
     * The damage a light-magic blow does to something in Shadow Form.
     *
     * <p>Pure, so the twenty percent can be pinned by a test rather than described in a comment.
     */
    public static float amplify(float amount) {
        return amount * (1.0f + LIGHT_VULNERABILITY);
    }

    /** Whether this entity is currently paying the light tax. */
    public static boolean isActive(LivingEntity entity) {
        return entity.hasEffect(ModEffects.SHADOW_FORM);
    }
}
