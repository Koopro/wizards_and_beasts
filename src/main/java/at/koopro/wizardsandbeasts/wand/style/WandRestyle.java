package at.koopro.wizardsandbeasts.wand.style;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.visual.WandMetal;
import at.koopro.wizardsandbeasts.wand.visual.WandStyle;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Restyling a wand: every look a player may change, the choices for each, what a change costs, and the restyled
 * wand. Shared by the wandmaker's bench (the player picks, {@code WandRestyleMenu}) and Ollivanders (a random new
 * look for a fee).
 *
 * <p>Only looks move. The restyled wand is a copy of the wand put in with its configuration (handle, shaft,
 * ornament) and its {@link WandStyle} (colour, grain, metal, core light) replaced; wood, core, length,
 * flexibility, condition, bond and history are carried over untouched.
 *
 * <p>A selection is one index per {@link Aspect}. Index 0 keeps what the wand shows now and costs nothing; index 1
 * returns the aspect to its wood's or core's own; the rest are the aspect's choices in a fixed order, the same on
 * client and server, so the screen can preview exactly what the server will make.
 */
@NullMarked
public final class WandRestyle {

    /** Index 0 of every aspect: leave it as it is. */
    public static final int KEEP = 0;
    /** Index 1 of every aspect: back to the wood's (or core's) own. */
    public static final int NATURAL = 1;

    public enum Aspect {
        HANDLE, SHAFT, ORNAMENT, COLOUR, GRAIN, METAL, LIGHT;

        public String key() {
            return "gui.wizards_and_beasts.wand_restyle.aspect." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A wood stain: its name and colour. */
    public record Swatch(String name, int rgb) {}

    /** Stains for the wood, from the palest to the darkest, then the coloured lacquers. */
    public static final List<Swatch> STAINS = List.of(
            new Swatch("bone", 0xEDE6D8), new Swatch("ash", 0xD9D2C2), new Swatch("straw", 0xD8C08A),
            new Swatch("honey", 0xC9A46A), new Swatch("amber", 0xC08A4E), new Swatch("rowan", 0xB0704A),
            new Swatch("chestnut", 0x8C5A3C), new Swatch("walnut", 0x5E4030), new Swatch("ebony", 0x2A2220),
            new Swatch("oxblood", 0x7A2E2A), new Swatch("moss", 0x5F7048), new Swatch("slate", 0x5A6470),
            new Swatch("ivory_lacquer", 0xF4F0E6), new Swatch("jet_lacquer", 0x141214));

    /** Lights for the core: the shipped cores' colours and a few no core gives. */
    public static final List<Swatch> LIGHTS = List.of(
            new Swatch("heart_red", 0xE8352B), new Swatch("ember", 0xFF9A4A), new Swatch("gold", 0xFFD27A),
            new Swatch("moon_white", 0xEEF4FF), new Swatch("storm_blue", 0x8CCBFF), new Swatch("dusk_violet", 0x7E6AA8),
            new Swatch("leaf_green", 0x7FD28A), new Swatch("rose", 0xF29BC8), new Swatch("blood", 0x7A2E46),
            new Swatch("sea_teal", 0x4FD6C0));

    /** The painted grain sheets ({@code textures/item/wand/grain_*.png}). */
    public static final List<String> GRAINS = List.of(
            "straight", "fine", "wavy", "figured", "banded", "flecked", "knotty", "nodal", "pored", "stringy");

    private WandRestyle() {}

    // ── choices ──────────────────────────────────────────────────────────

    /** How many indices this aspect has, KEEP and NATURAL included. */
    public static int size(Aspect aspect) {
        return 2 + switch (aspect) {
            case HANDLE -> modules(WandSlot.HANDLE).size();
            case SHAFT -> modules(WandSlot.SHAFT).size();
            case ORNAMENT -> modules(WandSlot.ORNAMENT).size();
            case COLOUR -> STAINS.size();
            case GRAIN -> GRAINS.size();
            case METAL -> WandMetal.values().length;
            case LIGHT -> LIGHTS.size();
        };
    }

    /** The modules a slot offers, in a fixed order (by id). */
    public static List<Identifier> modules(WandSlot slot) {
        return WandModuleRegistry.getAllForSlot(slot).stream().map(WandModule::id)
                .sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    /** The name of choice {@code index} of an aspect, for the screen and the tooltip. */
    public static Component label(Aspect aspect, int index) {
        if (index == KEEP) {
            return Component.translatable("gui.wizards_and_beasts.wand_restyle.keep");
        }
        if (index == NATURAL) {
            return Component.translatable("gui.wizards_and_beasts.wand_restyle.natural");
        }
        int i = index - 2;
        String key = "gui.wizards_and_beasts.wand_restyle.";
        return switch (aspect) {
            case HANDLE, SHAFT, ORNAMENT -> {
                Identifier id = modules(slot(aspect)).get(i);
                yield Component.translatableWithFallback("wand_module." + id.getNamespace() + "." + id.getPath(),
                        pretty(id.getPath()));
            }
            case COLOUR -> Component.translatableWithFallback(key + "stain." + STAINS.get(i).name(),
                    pretty(STAINS.get(i).name()));
            case GRAIN -> Component.translatableWithFallback(key + "grain." + GRAINS.get(i), pretty(GRAINS.get(i)));
            case METAL -> Component.translatable(key + "metal." + WandMetal.values()[i].getSerializedName());
            case LIGHT -> Component.translatableWithFallback(key + "light." + LIGHTS.get(i).name(),
                    pretty(LIGHTS.get(i).name()));
        };
    }

    /** A swatch colour to draw beside the label, or -1 when the choice has none. */
    public static int swatch(Aspect aspect, int index) {
        if (index < 2) {
            return -1;
        }
        return switch (aspect) {
            case COLOUR -> STAINS.get(index - 2).rgb();
            case LIGHT -> LIGHTS.get(index - 2).rgb();
            case METAL -> switch (WandMetal.values()[index - 2]) {
                case PEWTER -> 0x8D877C;
                case SILVER -> 0xC9D2DC;
                case GOLD -> 0xE0B04A;
            };
            default -> -1;
        };
    }

    private static WandSlot slot(Aspect aspect) {
        return switch (aspect) {
            case HANDLE -> WandSlot.HANDLE;
            case SHAFT -> WandSlot.SHAFT;
            default -> WandSlot.ORNAMENT;
        };
    }

    private static String pretty(String path) {
        String words = path.replace('_', ' ');
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    // ── the restyled wand ────────────────────────────────────────────────

    /** Whether any aspect asks for a change. */
    public static boolean changes(int[] selection) {
        return Arrays.stream(selection).anyMatch(i -> i != KEEP);
    }

    /**
     * The wand with this selection applied: a copy, one wand, with only its configuration and style replaced.
     * {@code registries} is needed to know a wood's own handle and shaft for "natural" and to keep the shape a
     * wand shows now when only its colour changes.
     */
    public static ItemStack apply(HolderLookup.@Nullable Provider registries, ItemStack wand, int[] selection) {
        ItemStack out = wand.copyWithCount(1);
        WandConfiguration shape = WandAppearance.configuration(registries, wand);
        WandConfiguration natural = WandAppearance.configurationFor(
                WandAppearance.definition(registries, WandComponents.getWood(wand)), -1);
        boolean shapeChanged = false;
        for (Aspect aspect : new Aspect[] {Aspect.HANDLE, Aspect.SHAFT, Aspect.ORNAMENT}) {
            int index = at(selection, aspect);
            if (index == KEEP) {
                continue;
            }
            WandSlot slot = slot(aspect);
            Optional<Identifier> chosen = index == NATURAL ? natural.getModule(slot)
                    : Optional.of(modules(slot).get(index - 2));
            shape = chosen.isPresent() ? shape.withModule(slot, chosen.get()) : shape.withoutModule(slot);
            shapeChanged = true;
        }
        if (shapeChanged) {
            out.set(WandComponents.WAND_CONFIGURATION.get(), shape);
        }

        WandStyle style = wand.getOrDefault(WandComponents.WAND_STYLE.get(), WandStyle.NONE);
        style = switch (at(selection, Aspect.COLOUR)) {
            case KEEP -> style;
            case NATURAL -> style.withTint(Optional.empty());
            default -> style.withTint(Optional.of(STAINS.get(at(selection, Aspect.COLOUR) - 2).rgb()));
        };
        style = switch (at(selection, Aspect.GRAIN)) {
            case KEEP -> style;
            case NATURAL -> style.withGrain(Optional.empty());
            default -> style.withGrain(Optional.of(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                    GRAINS.get(at(selection, Aspect.GRAIN) - 2))));
        };
        style = switch (at(selection, Aspect.METAL)) {
            case KEEP -> style;
            case NATURAL -> style.withMetal(Optional.empty());
            default -> style.withMetal(Optional.of(WandMetal.values()[at(selection, Aspect.METAL) - 2]));
        };
        style = switch (at(selection, Aspect.LIGHT)) {
            case KEEP -> style;
            case NATURAL -> style.withGlint(Optional.empty());
            default -> style.withGlint(Optional.of(LIGHTS.get(at(selection, Aspect.LIGHT) - 2).rgb()));
        };
        if (style.isEmpty()) {
            out.remove(WandComponents.WAND_STYLE.get());
        } else {
            out.set(WandComponents.WAND_STYLE.get(), style);
        }
        return out;
    }

    private static int at(int[] selection, Aspect aspect) {
        int i = aspect.ordinal();
        return i < selection.length ? selection[i] : KEEP;
    }

    // ── cost ─────────────────────────────────────────────────────────────

    /** What one aspect's change is paid with: {@code count} of anything in {@code tag}, or of {@code item}. */
    public record Cost(@Nullable TagKey<Item> tag, @Nullable Item item, int count, String what) {
        public boolean matches(ItemStack stack) {
            return tag != null ? stack.is(tag) : item != null && stack.is(item);
        }

        public Component label() {
            return Component.translatable("gui.wizards_and_beasts.wand_restyle.cost." + what, count);
        }
    }

    private static final TagKey<Item> DYES = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            Identifier.fromNamespaceAndPath("c", "dyes"));

    /**
     * The bench's price for this selection, one entry per changed aspect. Wood work (handle, shaft, ornament,
     * grain) costs a log; a stain, a dye; a core light, glowstone dust; metal by what it is, gold dearest.
     */
    public static List<Cost> cost(int[] selection) {
        List<Cost> out = new ArrayList<>();
        for (Aspect aspect : Aspect.values()) {
            int index = at(selection, aspect);
            if (index == KEEP) {
                continue;
            }
            out.add(switch (aspect) {
                case HANDLE, SHAFT, ORNAMENT, GRAIN -> new Cost(ItemTags.LOGS, null, 1, "log");
                case COLOUR -> new Cost(DYES, null, 1, "dye");
                case LIGHT -> new Cost(null, Items.GLOWSTONE_DUST, 1, "glowstone");
                case METAL -> metalCost(index == NATURAL ? null : WandMetal.values()[index - 2]);
            });
        }
        return merge(out);
    }

    private static Cost metalCost(@Nullable WandMetal metal) {
        if (metal == null || metal == WandMetal.PEWTER) {
            return new Cost(null, Items.IRON_NUGGET, 3, "iron_nugget");
        }
        return metal == WandMetal.SILVER ? new Cost(null, Items.IRON_INGOT, 2, "iron_ingot")
                : new Cost(null, Items.GOLD_INGOT, 4, "gold_ingot");
    }

    /** Two changes paid with the same thing are one line with both counts. */
    private static List<Cost> merge(List<Cost> costs) {
        List<Cost> out = new ArrayList<>();
        for (Cost c : costs) {
            int at = -1;
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).what().equals(c.what())) {
                    at = i;
                }
            }
            if (at < 0) {
                out.add(c);
            } else {
                Cost had = out.get(at);
                out.set(at, new Cost(had.tag(), had.item(), had.count() + c.count(), had.what()));
            }
        }
        return out;
    }

    /** How many of {@code cost} the items hold. */
    public static int held(List<ItemStack> items, Cost cost) {
        int n = 0;
        for (ItemStack stack : items) {
            if (cost.matches(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    // ── Ollivanders ──────────────────────────────────────────────────────

    /**
     * A whole new look picked at random: every aspect set to one of its real choices (never "keep" or
     * "natural"), and never the very look the wand already wears.
     */
    public static int[] random(RandomSource random, @Nullable WandStyle current,
                               @Nullable WandConfiguration currentShape) {
        int[] pick = new int[Aspect.values().length];
        for (int attempt = 0; attempt < 8; attempt++) {
            for (Aspect aspect : Aspect.values()) {
                pick[aspect.ordinal()] = 2 + random.nextInt(size(aspect) - 2);
            }
            // An ornament is not every wand's: about half of Ollivander's restyles come back plain.
            if (random.nextBoolean()) {
                pick[Aspect.ORNAMENT.ordinal()] = NATURAL;
            }
            if (!sameAs(pick, current, currentShape)) {
                break;
            }
        }
        return pick;
    }

    private static boolean sameAs(int[] pick, @Nullable WandStyle current, @Nullable WandConfiguration shape) {
        if (current == null || shape == null) {
            return false;
        }
        Optional<Integer> tint = Optional.of(STAINS.get(pick[Aspect.COLOUR.ordinal()] - 2).rgb());
        Optional<Identifier> handle = Optional.of(modules(WandSlot.HANDLE).get(pick[Aspect.HANDLE.ordinal()] - 2));
        return tint.equals(current.tint()) && handle.equals(shape.getModule(WandSlot.HANDLE));
    }
}
