package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * The {@code wizards_and_beasts:beam_shape} registry of {@link BeamShape} codecs, plus the dispatch
 * codec that (de)serialises a {@code BeamShape} by its {@code type} field.
 *
 * <p>Registered on the <strong>client</strong> mod bus only (see {@link #init}) — a beam is a
 * client-only visual, and keeping the registry off the server side-steps loading the client-only
 * render code during a dedicated-server registry pass.
 */
public final class ModBeamShapes {

    public static final ResourceKey<Registry<MapCodec<? extends BeamShape>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_shape"));

    /** Created eagerly so the dispatch codec can bind; made known to the game in {@link #init}. */
    public static final Registry<MapCodec<? extends BeamShape>> REGISTRY =
            new RegistryBuilder<>(REGISTRY_KEY).sync(false).create();

    /** Serialises any registered shape by its registry name under a {@code type} key. */
    public static final Codec<BeamShape> DISPATCH_CODEC =
            REGISTRY.byNameCodec().dispatch(BeamShape::codec, codec -> codec);

    private static final DeferredRegister<MapCodec<? extends BeamShape>> SHAPES =
            DeferredRegister.create(REGISTRY_KEY, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<MapCodec<? extends BeamShape>, MapCodec<Laser>> LASER =
            SHAPES.register("laser", () -> Laser.CODEC);

    public static final DeferredHolder<MapCodec<? extends BeamShape>, MapCodec<Lightning>> LIGHTNING =
            SHAPES.register("lightning", () -> Lightning.CODEC);

    private ModBeamShapes() {}

    /** Call from the client mod-bus constructor. Registers the registry, then its contents. */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener((NewRegistryEvent event) -> event.register(REGISTRY));
        SHAPES.register(modEventBus);
    }
}
