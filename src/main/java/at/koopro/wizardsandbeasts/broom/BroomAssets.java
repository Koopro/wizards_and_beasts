package at.koopro.wizardsandbeasts.broom;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Which geometry, texture and animation file a broom draws with.
 *
 * <p>All three are optional and all three fall back to the base {@code broom} asset, so a definition
 * that names none of them renders exactly as it did before these keys existed. That is the point:
 * the master rig holds every slot variant at once, and swapping the whole geometry is the escape
 * hatch for a broom that cannot be assembled out of the parts that ship — not the normal route.
 *
 * <h2>Path conventions</h2>
 * {@code model} and {@code animation} are <b>GeckoLib asset subpaths</b>: {@code modid:broom_firebolt}
 * resolves to {@code geckolib/models/entity/broom_firebolt.geo.json} and
 * {@code geckolib/animations/entity/broom_firebolt.animation.json} respectively.
 *
 * <p>{@code texture} accepts either form. A path already starting with {@code textures/} is used
 * verbatim — {@code modid:textures/entity/broom/firebolt.png} — and anything else is treated as a
 * subpath under {@code textures/entity/}, so {@code modid:broom/firebolt} means the same file. Both
 * are supported because the full path is what a datapack author expects to write and the subpath is
 * what GeckoLib's own helpers produce; rejecting either would be a papercut for no gain.
 *
 * <h2>Swapping the model is not free</h2>
 * {@code BroomRenderer} shows one variant per slot by hiding every other variant it knows about, and
 * it addresses them by name. A replacement geometry that does not carry those bones simply has
 * nothing hidden and nothing shown, which is correct but means the slot system stops applying to it.
 * A replacement that carries <em>some</em> of them gets a partial rig. Keep {@code broom_body} in any
 * case, or the broom will not tilt.
 */
public record BroomAssets(
        Optional<Identifier> model,
        Optional<Identifier> texture,
        Optional<Identifier> animation) {

    /** What a broom gets when it names none of the three: the shared rig, sheet and clips. */
    public static final BroomAssets DEFAULT =
            new BroomAssets(Optional.empty(), Optional.empty(), Optional.empty());

    /** Marks a {@code texture} value that is already a complete path rather than a subpath. */
    private static final String FULL_TEXTURE_PREFIX = "textures/";

    /** The key this field carried for one day before the brief renamed it. Still accepted. */
    private static final String LEGACY_TEXTURE_KEY = "entity_texture";

    static BroomAssets decode(BroomFields<?> fields) {
        return new BroomAssets(
                fields.maybe("model", Identifier.CODEC),
                fields.maybeAny(Identifier.CODEC, "texture", LEGACY_TEXTURE_KEY),
                fields.maybe("animation", Identifier.CODEC));
    }

    /** True when {@link #texture()} is a complete resource path and must not be reformatted. */
    public boolean textureIsFullPath() {
        return texture.map(id -> id.getPath().startsWith(FULL_TEXTURE_PREFIX)).orElse(false);
    }

    <T> void encode(RecordBuilder<T> builder, DynamicOps<T> ops) {
        model.ifPresent(id ->
                builder.add("model", Identifier.CODEC.encodeStart(ops, id).result().orElseThrow()));
        texture.ifPresent(id ->
                builder.add("texture", Identifier.CODEC.encodeStart(ops, id).result().orElseThrow()));
        animation.ifPresent(id ->
                builder.add("animation", Identifier.CODEC.encodeStart(ops, id).result().orElseThrow()));
    }
}
