package at.koopro.wizardsandbeasts.client.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.trinket.SneakoscopeItem;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTier;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTuning;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * How far round its turn the Sneakoscope is, as a fraction of one revolution in {@code [0, 1)}.
 *
 * <p>Item models cannot animate, so the spin is a {@code minecraft:range_dispatch} over
 * {@link SneakoscopeTuning#SPIN_FRAMES} pre-rotated model frames and this property is the clock that
 * walks between them. Rate comes from the stack's own {@code last_threat_count} and focus
 * components, which means the top on the ground, the top in your hand and the top in someone else's
 * hand all spin at the rate their own reading justifies, with no per-frame lookup of anything
 * outside the stack.
 *
 * <p>At {@link SneakoscopeTier#CALM} the phase is a hard zero and the model sits on frame 0. A
 * Sneakoscope idling with a slow decorative turn would be lying: the whole information content of
 * the object is the difference between still and not still.
 *
 * <p>No {@code @OnlyIn}: NeoForge no longer strips members for it and now logs its use as an error.
 * The class is client-only by reachability instead — nothing but {@code WizardsAndBeastsClient}'s
 * mod-bus listener names it, and that never runs on a dedicated server.
 */
public record SneakoscopeSpinProperty() implements RangeSelectItemModelProperty {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sneakoscope_spin");

    public static final MapCodec<SneakoscopeSpinProperty> MAP_CODEC =
            MapCodec.unit(new SneakoscopeSpinProperty());

    public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(ID, MAP_CODEC);
    }

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        SneakoscopeTier tier = SneakoscopeTuning.tier(SneakoscopeItem.threatCount(stack));
        boolean focused = SneakoscopeItem.focusOf(stack).active();
        // Wall clock rather than game time: this is asked once per frame, and quantising the phase
        // to 20 Hz would turn a smooth spin into a visible stutter on top of the LOW-tier wobble
        // that is *meant* to be one.
        return SneakoscopeTuning.spinPhase(System.currentTimeMillis(), tier, focused);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}
