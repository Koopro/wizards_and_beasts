package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.gillyweed.Gillyweed;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Gills, webbed hands, and the lake stops being a place you visit.
 *
 * <p>The swim speed rides here as an attribute modifier on NeoForge's {@code SWIM_SPEED}, so it
 * begins and ends exactly with the gills and cannot be left behind by an effect that was removed
 * some way this mod did not anticipate. Everything else about the transformation — the breathing,
 * the warning, the gill particles, the green cast — lives in {@code GillyweedHandler} and the render
 * layer, because none of it is expressible as an attribute.
 */
public final class GillsEffect extends MobEffect {

    /** Lake green. */
    private static final int COLOUR = 0x4E8C5A;

    public GillsEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
        addAttributeModifier(
                net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gillyweed_swim"),
                Gillyweed.SWIM_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
