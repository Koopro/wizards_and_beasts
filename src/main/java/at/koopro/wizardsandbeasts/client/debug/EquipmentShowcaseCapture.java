package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.character.CharacterSheetScreen;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import at.koopro.wizardsandbeasts.registry.ArmorItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs the local player in a series of outfits — vanilla armour, robes, hood, cloak, hat, mask,
 * necklace, the Deathly Hallow — from every side, sneaking, walking, in first person, in the inventory
 * preview and on the Character Sheet.
 *
 * <p>Armour hiding and Curios-drawn GeckoLib clothing are only judged by looking at them: clipping,
 * skin gaps, floating pieces and z-fighting have no assertion. This drives the real client through
 * the real equipment path ({@link EquipmentAPI} on the server, Curios sync, the render-state modifier,
 * {@code WornGeoCurioRenderer}) so the pictures show what a player would see.
 *
 * <p>Development only, inert unless asked for: set {@code WB_EQUIPMENT_SHOWCASE=1}.
 * {@code WB_EQUIPMENT_SHOWCASE_WORLD} names a disposable save that must already exist (default
 * {@code wb_equipment_capture}); {@code WB_EQUIPMENT_SHOWCASE_OUT} the folder under
 * {@code screenshots/} (default {@code equipment_showcase}). Shots are {@code <outfit>__<view>.png};
 * the client quits after the last one and logs which player model (slim/classic) it drew.
 *
 * <p>{@code WB_EQUIPMENT_SHOWCASE=cast} photographs the cast poses instead: a robed wizard with a wand, each
 * cast phase parked by the same {@code SpellCastAnimationS2CPayload} a real cast sends, from three sides and in
 * first person, plus a strike caught mid-release from a timed cast.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class EquipmentShowcaseCapture {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean ENABLED =
            System.getenv("WB_EQUIPMENT_SHOWCASE") != null && !FMLEnvironment.isProduction();
    private static final boolean CAST = "cast".equals(System.getenv("WB_EQUIPMENT_SHOWCASE"));
    /** Wardrobe Renaissance Phase D: the five prototype cuts standing, walking, sprinting, sneaking, seated. */
    private static final boolean WARDROBE = "wardrobe".equals(System.getenv("WB_EQUIPMENT_SHOWCASE"));
    /** Wearable icons pass (2026-11-05): every wearable's inventory icon, then the Curios screen's empty slots. */
    private static final boolean ICONS = "icons".equals(System.getenv("WB_EQUIPMENT_SHOWCASE"));
    private static final int LOAD_TICKS = 80;
    private static final int STAGE_Y = 200;
    private static final double START_X = -10.5;
    /** Centre of the stage's pool (wardrobe mode swims there), well east of where the walks end. */
    private static final double POOL_X = 9.5;

    private enum Stage { IDLE, OPENING, LOADING, RUNNING, DONE }

    /** Where the camera sits relative to an east-facing player; SELF is the player's own eyes. */
    private enum Cam { QUARTER, FRONT, SIDE, BACK, SELF, SCREEN }

    private record Outfit(String name, Consumer<ServerPlayer> dress) {}

    private record Step(String name, int ticks, boolean shoot, Cam cam,
                        Consumer<LocalPlayer> client, Consumer<ServerPlayer> server) {}

    private static final List<Outfit> OUTFITS = List.of(
            new Outfit("armor_only", p -> netherite(p, true, true, true, true)),
            new Outfit("armor_under_robe", p -> {
                netherite(p, true, true, true, true);
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()));
            }),
            new Outfit("robe_hood_up", p -> {
                netherite(p, true, true, true, true);
                ItemStack robe = new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get());
                RobeHood.setUp(robe, true);
                wear(p, robe);
            }),
            new Outfit("student_set_hat", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
                wear(p, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()));
                wear(p, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get()));
            }),
            new Outfit("auror_mask_over_helmet", p -> {
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.AUROR_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.AUROR_ROBE_BOOTS.get()));
                wear(p, new ItemStack(ArmorItemRegistry.AUROR_ROBE_CHEST.get()));
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK_3.get()));
            }),
            new Outfit("full_outfit", p -> {
                netherite(p, true, true, false, false);
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_BOOTS.get()));
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()));
                wear(p, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()));
                wear(p, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get()));
                wear(p, new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK_1.get()));
                wear(p, new ItemStack(TrinketItemRegistry.TIME_TURNER.get()));
            }),
            new Outfit("deathly_hallow", p -> {
                netherite(p, true, true, true, true);
                wear(p, new ItemStack(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get()));
            }));

    /** The cast mode's one subject: a student with an ash wand, so the arm reads against plain cloth. */
    private static final List<Outfit> CAST_OUTFITS = List.of(new Outfit("cast", p -> {
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
        wear(p, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()));
        ItemStack wand = new ItemStack(at.koopro.wizardsandbeasts.registry.WandItemRegistry.WAND.get());
        wand.set(at.koopro.wizardsandbeasts.wand.WandComponents.WAND_WOOD.get(),
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ash"));
        p.setItemSlot(EquipmentSlot.MAINHAND, wand);
    }));

    /** One per prototype cut, each at the rank that shows the most of it (WARDROBE_RENAISSANCE Phase C). */
    /** The wearables whose inventory icons {@code tools/wearable_icons.py} draws, plus the Probity Probe. */
    private static final List<String> ICON_IDS = List.of(
            "student_robe_chest", "auror_robe_chest", "death_eater_robe_chest", "wizard_robe_chest",
            "scholar_gown_chest", "travel_coat_chest", "healer_coat_chest", "ministry_robe_chest",
            "master_mantle_chest", "quidditch_robes", "student_robe_legs", "student_robe_boots",
            "auror_robe_legs", "auror_robe_boots", "death_eater_robe_legs", "death_eater_robe_boots",
            "school_cape", "travel_cloak", "invisibility_cloak", "deathly_hallow_cloak", "shield_cloak",
            "wizard_hat", "sorting_hat", "headless_hat", "professor_hat", "travel_hat", "shield_hat",
            "death_eater_mask", "death_eater_mask_1", "death_eater_mask_2", "death_eater_mask_3",
            "death_eater_mask_4", "death_eater_mask_5", "gloves", "dragon_hide_gloves", "shield_gloves",
            "leather_belt", "duty_belt", "scarf", "time_turner", "probity_probe");

    private static final List<Outfit> ICON_OUTFITS = List.of(new Outfit("icons", p -> {}));

    /** Fills the main inventory (slots 0..35) with {@code ICON_IDS[from..]}, the rest empty. */
    private static void stock(ServerPlayer p, int from) {
        undress(p);
        p.getInventory().clearContent();
        for (int i = from; i < ICON_IDS.size() && i - from < 36; i++) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, ICON_IDS.get(i));
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            if (item == Items.AIR) {
                LOGGER.warn("Icon showcase: no item {}", id);
                continue;
            }
            // Main grid first (9..35), then the hotbar (0..8), so the order reads top-left to bottom-right.
            int n = i - from;
            p.getInventory().setItem(n < 27 ? 9 + n : n - 27, new ItemStack(item));
        }
        p.inventoryMenu.broadcastChanges();
    }

    /**
     * Opens the Curios screen from the server. The mod compiles against the Curios API only, which has no
     * open call; Curios' own key is ignored unless the window has focus, which a hidden-desktop client never
     * has. Reflection keeps this dev-only harness off Curios internals at compile time.
     */
    private static void openCurios(ServerPlayer p) {
        try {
            Object provider = Class.forName("top.theillusivec4.curios.common.inventory.container.CuriosMenuProvider")
                    .getConstructor().newInstance();
            p.openMenu((net.minecraft.world.MenuProvider) provider);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Icon showcase: cannot open the Curios screen", e);
        }
    }

    /**
     * Parks the cursor in the window's corner so no tooltip covers the grid. A hidden-desktop window never
     * gets OS cursor events, so {@code glfwSetCursorPos} alone does nothing; the mouse handler's own
     * position is what screens read, set here by reflection (dev harness only).
     */
    private static void parkCursor() {
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), 1.0, 1.0);
        for (String name : new String[]{"xpos", "ypos"}) {
            try {
                var field = mc.mouseHandler.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setDouble(mc.mouseHandler, 1.0);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Icon showcase: cannot park the cursor ({})", name, e);
            }
        }
    }

    private static void buildIconsScript() {
        SCRIPT.add(new Step("stock_a", 20, false, Cam.SCREEN, p -> {
            moves(false, false);
            parkCursor();
        }, p -> stock(p, 0)));
        SCRIPT.add(new Step("inventory_a", 10, true, Cam.SCREEN, p -> {
            Minecraft.getInstance().setScreen(new InventoryScreen(p));
            parkCursor();
        }, NOTHING));
        SCRIPT.add(new Step("close_a", 4, false, Cam.SCREEN, p -> Minecraft.getInstance().setScreen(null), NOTHING));
        SCRIPT.add(new Step("stock_b", 20, false, Cam.SCREEN, NONE, p -> stock(p, 36)));
        SCRIPT.add(new Step("curios_b", 20, true, Cam.SCREEN, p -> parkCursor(), EquipmentShowcaseCapture::openCurios));
        SCRIPT.add(new Step("close_b", 4, false, Cam.SCREEN, p -> p.closeContainer(), NOTHING));
    }

    private static final List<Outfit> WARDROBE_OUTFITS = List.of(
            new Outfit("school_lion_graduate", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
                wear(p, stitched(p, ArmorItemRegistry.STUDENT_ROBE_CHEST.get(), "house_lion", 2));
            }),
            new Outfit("auror_elite", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.AUROR_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.AUROR_ROBE_BOOTS.get()));
                wear(p, stitched(p, ArmorItemRegistry.AUROR_ROBE_CHEST.get(), "auror_office", 3));
            }),
            new Outfit("dark_officer_hood", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_BOOTS.get()));
                ItemStack robe = stitched(p, ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get(), "dark_order", 1);
                RobeHood.setUp(robe, true);
                wear(p, robe);
            }),
            new Outfit("everyday_moss", p ->
                    wear(p, stitched(p, ArmorItemRegistry.WIZARD_ROBE_CHEST.get(), "moss", 0))),
            new Outfit("scholar_potions_professor", p ->
                    wear(p, stitched(p, ArmorItemRegistry.SCHOLAR_GOWN_CHEST.get(), "faculty_potions", 1))),
            // Phase E1: capes over robes. The elytra steps check that a glider takes the back from the cape.
            new Outfit("cape_lion_over_school", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
                wear(p, stitched(p, ArmorItemRegistry.STUDENT_ROBE_CHEST.get(), "house_lion", 0));
                wear(p, stitched(p, ArmorItemRegistry.SCHOOL_CAPE.get(), "house_lion", 0));
            }),
            new Outfit("cloak_over_everyday", p -> {
                wear(p, stitched(p, ArmorItemRegistry.WIZARD_ROBE_CHEST.get(), "slate", 0));
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_CLOAK.get(), "umber", 0));
            }),
            new Outfit("cloak_over_dark_hood_down", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_BOOTS.get()));
                wear(p, stitched(p, ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get(), "dark_order", 0));
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_CLOAK.get(), "slate", 0));
            }),
            // Phase E2: accessories in their own Curios slots, over the robes.
            new Outfit("acc_student_scarf_gloves", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_BOOTS.get()));
                wear(p, stitched(p, ArmorItemRegistry.STUDENT_ROBE_CHEST.get(), "house_lion", 1));
                wear(p, stitched(p, ArmorItemRegistry.SCARF.get(), "house_lion", 0));
                wear(p, stitched(p, ArmorItemRegistry.GLOVES.get(), "leather_black", 0));
            }),
            new Outfit("acc_auror_duty_belt", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.AUROR_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.AUROR_ROBE_BOOTS.get()));
                wear(p, stitched(p, ArmorItemRegistry.AUROR_ROBE_CHEST.get(), "auror_office", 2));
                wear(p, stitched(p, ArmorItemRegistry.DUTY_BELT.get(), "auror_office", 0));
                wear(p, stitched(p, ArmorItemRegistry.GLOVES.get(), "dragonhide", 0));
            }),
            new Outfit("acc_traveller", p -> {
                wear(p, stitched(p, ArmorItemRegistry.WIZARD_ROBE_CHEST.get(), "moss", 0));
                wear(p, stitched(p, ArmorItemRegistry.LEATHER_BELT.get(), "leather_brown", 0));
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_CLOAK.get(), "umber", 0));
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_HAT.get(), "umber", 0));
            }),
            new Outfit("acc_professor", p -> {
                wear(p, stitched(p, ArmorItemRegistry.SCHOLAR_GOWN_CHEST.get(), "faculty_potions", 1));
                wear(p, stitched(p, ArmorItemRegistry.PROFESSOR_HAT.get(), "faculty_potions", 0));
                wear(p, stitched(p, ArmorItemRegistry.SCARF.get(), "house_serpent", 0));
            }),
            // Phase E3: the four remaining cuts, at the ranks that show the most of them.
            new Outfit("e3_travel_coat", p -> {
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_COAT_CHEST.get(), "canvas_umber", 0));
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_HAT.get(), "umber", 0));
            }),
            new Outfit("e3_healer_senior", p ->
                    wear(p, stitched(p, ArmorItemRegistry.HEALER_COAT_CHEST.get(), "ward_green", 2))),
            new Outfit("e3_ministry_council", p ->
                    wear(p, stitched(p, ArmorItemRegistry.MINISTRY_ROBE_CHEST.get(), "ministry_plum", 3))),
            new Outfit("e3_master_with_cape", p -> {
                wear(p, stitched(p, ArmorItemRegistry.MASTER_MANTLE_CHEST.get(), "master_crimson", 0));
                wear(p, stitched(p, ArmorItemRegistry.SCHOOL_CAPE.get(), "school_plain", 0));
            }),
            // The hood is up on the stack, but a hat is worn: the hood must be drawn down.
            new Outfit("acc_dark_hood_up_under_hat", p -> {
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_LEGS.get()));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_BOOTS.get()));
                ItemStack robe = stitched(p, ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get(), "dark_order", 0);
                RobeHood.setUp(robe, true);
                wear(p, robe);
                wear(p, stitched(p, ArmorItemRegistry.TRAVEL_HAT.get(), "slate", 0));
            }));

    /**
     * A cut stitched through the same door the admin command uses, so a livery registry that failed
     * to load shows up here as a warning rather than as a plain robe that looks like a pass.
     */
    private static ItemStack stitched(ServerPlayer p, net.minecraft.world.item.Item cut, String livery, int rank) {
        ItemStack stack = new ItemStack(cut);
        var result = at.koopro.wizardsandbeasts.equipment.wardrobe.Wardrobe.stamp(stack,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, livery),
                rank, p.level().registryAccess());
        LOGGER.info("Wardrobe showcase: {} in {} rank {}: {}", cut, livery, rank, result);
        return stack;
    }

    /** {@code WB_EQUIPMENT_SHOWCASE_ONLY=<prefix>} keeps only the outfits whose name starts with it. */
    private static final String ONLY = System.getenv().getOrDefault("WB_EQUIPMENT_SHOWCASE_ONLY", "");

    private static List<Outfit> outfits() {
        List<Outfit> all = ICONS ? ICON_OUTFITS : CAST ? CAST_OUTFITS : WARDROBE ? WARDROBE_OUTFITS : OUTFITS;
        if (ONLY.isEmpty()) {
            return all;
        }
        return all.stream().filter(o -> java.util.Arrays.stream(ONLY.split(","))
                .anyMatch(prefix -> o.name().startsWith(prefix))).toList();
    }

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static Stage stage = Stage.IDLE;
    private static int wait;
    private static int step;
    private static int outfit;
    private static boolean opened;
    private static boolean captureThisFrame;
    private static boolean forward;
    private static boolean sneak;
    private static boolean sprint;
    private static net.minecraft.world.entity.Entity seat;
    private static ArmorStand camera;

    private EquipmentShowcaseCapture() {}

    private static Path out() {
        return Path.of(Screenshot.SCREENSHOT_DIR,
                System.getenv().getOrDefault("WB_EQUIPMENT_SHOWCASE_OUT", "equipment_showcase"));
    }

    private static void netherite(ServerPlayer p, boolean head, boolean chest, boolean legs, boolean feet) {
        if (head) p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        if (chest) p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        if (legs) p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        if (feet) p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
    }

    private static void wear(ServerPlayer p, ItemStack stack) {
        if (!EquipmentAPI.tryEquip(p, stack)) {
            LOGGER.warn("Equipment showcase: could not wear {}", stack);
        }
    }

    private static void undress(ServerPlayer p) {
        EquipmentAPI.stripWearables(p, s -> false);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            p.setItemSlot(slot, ItemStack.EMPTY);
        }
        p.setInvisible(false);
    }

    private static final Consumer<LocalPlayer> NONE = p -> {};
    private static final Consumer<ServerPlayer> NOTHING = p -> {};

    private static void buildScript(Outfit current) {
        SCRIPT.clear();
        if (CAST) {
            buildCastScript(current);
            return;
        }
        if (WARDROBE) {
            buildWardrobeScript(current);
            return;
        }
        if (ICONS) {
            buildIconsScript();
            return;
        }
        SCRIPT.add(new Step("dress", 30, false, Cam.QUARTER, p -> moves(false, false), p -> {
            undress(p);
            p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
            current.dress().accept(p);
        }));
        SCRIPT.add(new Step("quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("front", 4, true, Cam.FRONT, NONE, NOTHING));
        SCRIPT.add(new Step("side", 4, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("back", 4, true, Cam.BACK, NONE, NOTHING));
        SCRIPT.add(new Step("sneak_quarter", 10, true, Cam.QUARTER, p -> moves(false, true), NOTHING));
        SCRIPT.add(new Step("walk_side", 12, true, Cam.SIDE, p -> moves(true, false), NOTHING));
        SCRIPT.add(new Step("stop", 6, false, Cam.SIDE, p -> moves(false, false), NOTHING));
        SCRIPT.add(new Step("first_person", 6, true, Cam.SELF, NONE, NOTHING));
        SCRIPT.add(new Step("inventory", 8, true, Cam.SCREEN,
                p -> Minecraft.getInstance().setScreen(new InventoryScreen(p)), NOTHING));
        SCRIPT.add(new Step("character_sheet", 10, true, Cam.SCREEN,
                p -> Minecraft.getInstance().setScreen(new CharacterSheetScreen()), NOTHING));
        SCRIPT.add(new Step("close", 2, false, Cam.QUARTER, p -> Minecraft.getInstance().setScreen(null), NOTHING));
    }

    /**
     * Still views, then the cloth in motion: four walk frames a few ticks apart so the stride is
     * caught at different phases, a sprint, a sneak, and seated in a boat (both legs forward, which is
     * what lifts the hip skirt's front).
     */
    private static void buildWardrobeScript(Outfit current) {
        SCRIPT.add(new Step("dress", 30, false, Cam.QUARTER, p -> moves(false, false), p -> {
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            undress(p);
            p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
            current.dress().accept(p);
        }));
        SCRIPT.add(new Step("quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("front", 4, true, Cam.FRONT, NONE, NOTHING));
        SCRIPT.add(new Step("side", 4, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("back", 4, true, Cam.BACK, NONE, NOTHING));
        SCRIPT.add(new Step("walk_side_a", 10, true, Cam.SIDE, p -> moves(true, false), NOTHING));
        SCRIPT.add(new Step("walk_side_b", 2, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("walk_side_c", 2, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("walk_quarter_a", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("walk_quarter_b", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("walk_front", 2, true, Cam.FRONT, NONE, NOTHING));
        SCRIPT.add(new Step("stop_walk", 8, false, Cam.SIDE, p -> moves(false, false), p ->
                p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true)));
        SCRIPT.add(new Step("sprint_side_a", 10, true, Cam.SIDE, p -> {
            sprint = true;
            moves(true, false);
        }, NOTHING));
        SCRIPT.add(new Step("sprint_side_b", 2, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("sprint_quarter", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("stop_sprint", 8, false, Cam.SIDE, p -> {
            sprint = false;
            moves(false, false);
        }, p -> p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true)));
        SCRIPT.add(new Step("sneak_quarter", 10, true, Cam.QUARTER, p -> moves(false, true), NOTHING));
        SCRIPT.add(new Step("sneak_side", 2, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("unsneak", 4, false, Cam.SIDE, p -> moves(false, false), NOTHING));
        SCRIPT.add(new Step("seat_set", 12, false, Cam.QUARTER, NONE, p -> {
            var boat = EntityType.OAK_BOAT.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (boat != null) {
                boat.snapTo(p.getX(), p.getY(), p.getZ(), -90.0F, 0.0F);
                p.level().addFreshEntity(boat);
                p.startRiding(boat, true, true);
                seat = boat;
            }
        }));
        SCRIPT.add(new Step("seated_quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("seated_side", 4, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("unseat", 6, false, Cam.QUARTER, NONE, EquipmentShowcaseCapture::dismount));

        // Astride a broom, in the flight pose layer's own riding pose.
        SCRIPT.add(new Step("broom_set", 14, false, Cam.QUARTER, NONE, p -> {
            var broom = new at.koopro.wizardsandbeasts.entity.broom.BroomEntity(
                    at.koopro.wizardsandbeasts.registry.ModEntities.BROOM.get(), p.level());
            broom.setPos(p.getX(), p.getY() + 1.0, p.getZ());
            broom.setYRot(-90.0F);
            broom.setDefinitionId(net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom"));
            p.level().addFreshEntity(broom);
            p.startRiding(broom, true, true);
            seat = broom;
            LOGGER.info("Wardrobe showcase: on the broom: {}", p.getVehicle() == broom);
        }));
        SCRIPT.add(new Step("broom_quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("broom_side", 4, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("unbroom", 6, false, Cam.QUARTER, NONE, EquipmentShowcaseCapture::dismount));

        // Swimming: into the stage's pool, sprinting forward, which is what puts a player in the swim pose.
        SCRIPT.add(new Step("swim_set", 16, false, Cam.SIDE, p -> {
            sprint = true;
            moves(true, false);
        }, p -> p.teleportTo(p.level(), POOL_X, STAGE_Y - 2.2, 0.5, Set.of(), -90.0F, 0.0F, true)));
        SCRIPT.add(new Step("swim_side", 2, true, Cam.SIDE, p -> LOGGER.info("Wardrobe showcase: swimming {}", p.isSwimming()), NOTHING));
        SCRIPT.add(new Step("swim_quarter", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("swim_stop", 6, false, Cam.SIDE, p -> {
            sprint = false;
            moves(false, false);
        }, p -> p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true)));

        // Gliding on an elytra worn under the robe: clothing never hides a glider.
        SCRIPT.add(new Step("elytra_set", 6, false, Cam.QUARTER, NONE, p -> {
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            p.teleportTo(p.level(), START_X, STAGE_Y + 14, 0.5, Set.of(), -90.0F, 10.0F, true);
        }));
        SCRIPT.add(new Step("elytra_launch", 8, false, Cam.SIDE, NONE, p -> {
            p.startFallFlying();
            LOGGER.info("Wardrobe showcase: gliding {}", p.isFallFlying());
        }));
        SCRIPT.add(new Step("elytra_side", 2, true, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("elytra_quarter", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("elytra_back", 2, true, Cam.BACK, NONE, NOTHING));
        SCRIPT.add(new Step("elytra_end", 6, false, Cam.QUARTER, NONE, p -> {
            p.stopFallFlying();
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
        }));
    }

    private static void dismount(ServerPlayer p) {
        p.stopRiding();
        if (seat != null) {
            seat.discard();
            seat = null;
        }
        p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
    }

    /** A cast parked on a phase (-1: none), sent exactly as the server sends a real one. */
    private static Consumer<ServerPlayer> castPhase(int phase) {
        return p -> at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload.send(p,
                new at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload(p.getId(),
                        "wizards_and_beasts:capture", phase < 0 ? 0 : 1, 0.35f, 0.6f, phase));
    }

    private static void buildCastScript(Outfit current) {
        SCRIPT.add(new Step("dress", 30, false, Cam.QUARTER, p -> moves(false, false), p -> {
            // The stage world hurt the subject (red flash, damage hearts in every shot): nothing here is about damage.
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            undress(p);
            p.teleportTo(p.level(), START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
            current.dress().accept(p);
        }));
        String[] phases = {"rest", "windup", "channel", "recovery"};
        for (int i = 0; i < phases.length; i++) {
            String phase = phases[i];
            SCRIPT.add(new Step(phase + "_set", 6, false, Cam.QUARTER, NONE, castPhase(i - 1)));
            SCRIPT.add(new Step(phase + "_quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
            SCRIPT.add(new Step(phase + "_side", 4, true, Cam.SIDE, NONE, NOTHING));
            SCRIPT.add(new Step(phase + "_front", 4, true, Cam.FRONT, NONE, NOTHING));
            SCRIPT.add(new Step(phase + "_first_person", 6, true, Cam.SELF, NONE, NOTHING));
        }
        // Wingardium Leviosa's swish (held, as its channel holds it) and flick, parked under its own spell id.
        for (int i = 1; i <= 2; i++) {
            int held = i;
            SCRIPT.add(new Step("leviosa_" + (i == 1 ? "swish" : "flick") + "_set", 6, false, Cam.QUARTER, NONE, p ->
                    at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload.send(p,
                            new at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload(p.getId(),
                                    "wizards_and_beasts:wingardium_leviosa", 1, 0.35f, 0.6f, held))));
            SCRIPT.add(new Step("leviosa_" + (i == 1 ? "swish" : "flick") + "_front", 4, true, Cam.FRONT, NONE, NOTHING));
            SCRIPT.add(new Step("leviosa_" + (i == 1 ? "swish" : "flick") + "_quarter", 4, true, Cam.QUARTER, NONE, NOTHING));
        }
        // A strike caught in motion: a 40-tick cast, shot just after the release window opens.
        SCRIPT.add(new Step("strike_set", 15, false, Cam.SIDE, NONE, p ->
                at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload.send(p,
                        new at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload(p.getId(),
                                "wizards_and_beasts:capture", 40, 0.35f, 0.6f, -1))));
        SCRIPT.add(new Step("strike_side", 1, true, Cam.SIDE, NONE, NOTHING));

        // A real cast through the gameplay path, not a parked pose: the use key held and let go, so the client sends
        // the real use and release packets and the server's own cast moments send every pose (CastAnimationSync).
        // (Starting the hold on the server alone made the client let go at once: vanilla releases a use whose key
        // is not down.)
        SCRIPT.add(new Step("real_ready", 10, false, Cam.SIDE, NONE, EquipmentShowcaseCapture::readyRealCaster));
        SCRIPT.add(new Step("real_hold_set", 8, false, Cam.SIDE,
                p -> Minecraft.getInstance().options.keyUse.setDown(true), NOTHING));
        SCRIPT.add(new Step("real_hold_side", 2, true, Cam.SIDE, EquipmentShowcaseCapture::logClientCast, p ->
                LOGGER.info("Cast showcase: server hold: usingWand={} session={}",
                        at.koopro.wizardsandbeasts.item.wand.WandItem.isUsingWand(p),
                        at.koopro.wizardsandbeasts.spell.cast.WandCastSessions.peek(p) != null)));
        SCRIPT.add(new Step("real_hold_quarter", 2, true, Cam.QUARTER, NONE, NOTHING));
        SCRIPT.add(new Step("real_release_set", 3, false, Cam.SIDE,
                p -> Minecraft.getInstance().options.keyUse.setDown(false), NOTHING));
        SCRIPT.add(new Step("real_strike_side", 1, true, Cam.SIDE, EquipmentShowcaseCapture::logClientCast, p -> {
            var data = p.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.SPELL_DATA.get());
            LOGGER.info("Cast showcase: real cast of {} {}", REAL_SPELL,
                    data.isOnCooldown(realSpellId(), p.level().getGameTime()) ? "SUCCEEDED (on cooldown)" : "did NOT cast");
        }));
        SCRIPT.add(new Step("real_settle_wait", 4, false, Cam.SIDE, NONE, NOTHING));
        SCRIPT.add(new Step("real_settle_side", 1, true, Cam.SIDE, NONE, NOTHING));
    }

    private static void logClientCast(LocalPlayer player) {
        long now = at.koopro.wizardsandbeasts.client.pose.ClientCastAnimationState.clientTick();
        var cast = at.koopro.wizardsandbeasts.client.pose.ClientCastAnimationState.get(player.getId(), now);
        LOGGER.info("Cast showcase: client cast state: {}", cast == null ? "none" : cast);
    }

    /** The lifecycle game tests' own spell, known to cast through this path ({@code WandCastLifecycleTests}). */
    private static final String REAL_SPELL = "arresto_momentum";

    private static String realSpellId() {
        var spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(REAL_SPELL);
        return spell == null ? REAL_SPELL : spell.getId();
    }

    private static void readyRealCaster(ServerPlayer p) {
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.makeWandkind(p);
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.giveBondedWand(p);
        at.koopro.wizardsandbeasts.gametest.WizardTestSupport.settleAllegiance(p);
        var data = p.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.SPELL_DATA.get());
        data.learnSpell(realSpellId());
        var prerequisite = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wingardium_leviosa");
        if (prerequisite != null) {
            data.learnSpell(prerequisite.getId());
        }
        data.setLoadoutSpell(0, realSpellId());
        data.setActiveSlot(0);
        data.setCooldown(realSpellId(), 0L);
        data.setGlobalCooldownEndTick(0L);
    }

    private static void moves(boolean forward, boolean sneak) {
        EquipmentShowcaseCapture.forward = forward;
        EquipmentShowcaseCapture.sneak = sneak;
        Minecraft.getInstance().options.keyShift.setDown(sneak);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ENABLED || stage != Stage.RUNNING || player == null) {
            return;
        }
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setYBodyRot(-90.0F);
        player.setXRot(0.0F);
        player.zza = forward ? 1.0F : 0.0F;
        player.xxa = 0.0F;
        player.input.keyPresses = new Input(forward, false, false, false, false, sneak, sprint);
        if (sprint && forward) {
            player.setSprinting(true);
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.bobView().set(false);
        mc.options.hideGui = true;
        mc.options.particles().set(net.minecraft.server.level.ParticleStatus.MINIMAL);
        mc.debugEntries.setStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES,
                net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900);
        stage = Stage.OPENING;
        String world = System.getenv().getOrDefault("WB_EQUIPMENT_SHOWCASE_WORLD", "wb_equipment_capture");
        LOGGER.info("Equipment showcase: {} outfits into {}", outfits().size(), out());
        mc.createWorldOpenFlows().openWorld(world, () -> stage = Stage.DONE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == Stage.IDLE || stage == Stage.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        if (stage == Stage.OPENING) {
            stage = Stage.LOADING;
            wait = LOAD_TICKS;
            server.execute(() -> buildStage(server));
            return;
        }
        if (stage == Stage.RUNNING) {
            follow(mc.player, SCRIPT.get(step).cam());
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == Stage.LOADING) {
            stage = Stage.RUNNING;
            LOGGER.info("Equipment showcase: player model is {}", mc.player.getSkin().model());
            outfit = 0;
            step = 0;
            buildScript(outfits().getFirst());
            begin(mc, server);
            return;
        }
        Step current = SCRIPT.get(step);
        if (current.shoot()) {
            mc.gui.getChat().clearMessages(false);
            mc.getToastManager().clear();
            mc.options.hideGui = current.cam() != Cam.SELF;
            captureThisFrame = true;
        } else {
            advance(mc, server);
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        String name = outfits().get(outfit).name() + "__" + SCRIPT.get(step).name() + ".png";
        Path file = mc.gameDirectory.toPath().resolve(out()).resolve(name);
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Equipment showcase: cannot write {}", file, e);
            }
        }));
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            advance(mc, server);
        }
    }

    private static void advance(Minecraft mc, MinecraftServer server) {
        step++;
        if (step >= SCRIPT.size()) {
            step = 0;
            outfit++;
            if (outfit >= outfits().size()) {
                stage = Stage.DONE;
                moves(false, false);
                LOGGER.info("Equipment showcase: done");
                mc.stop();
                return;
            }
            buildScript(outfits().get(outfit));
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (mc.player != null) {
            current.client().accept(mc.player);
        }
        var uuid = mc.player != null ? mc.player.getUUID() : null;
        server.execute(() -> {
            ServerPlayer player = uuid != null ? server.getPlayerList().getPlayer(uuid) : null;
            if (player != null) {
                current.server().accept(player);
            }
        });
    }

    private static void follow(LocalPlayer player, Cam cam) {
        Minecraft mc = Minecraft.getInstance();
        if (cam == Cam.SELF || cam == Cam.SCREEN) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.getCameraEntity() != player) {
                mc.setCameraEntity(player);
            }
            return;
        }
        if (camera == null || camera.level() != mc.level) {
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            camera.setInvisible(true);
        }
        double distance = 2.1;
        double angle = switch (cam) {
            case SIDE -> 0.0;
            case FRONT -> 90.0;
            case BACK -> 270.0;
            default -> 40.0;
        };
        double rad = Math.toRadians(angle);
        double camX = player.getX() + Math.sin(rad) * distance;
        double camZ = player.getZ() + Math.cos(rad) * distance;
        double lookY = player.getY() + 1.0;
        double camY = lookY + 0.25;
        double dx = player.getX() - camX;
        double dz = player.getZ() - camZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(lookY - camY, Math.sqrt(dx * dx + dz * dz)));
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(camX, camY - camera.getEyeHeight(), camZ);
        camera.setYRot(yaw);
        camera.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
        camera.setYHeadRot(yaw);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
            camera.xo = camX;
            camera.yo = camY - camera.getEyeHeight();
            camera.zo = camZ;
        }
    }

    /** A stone platform in clear sky, noon, survival. */
    private static void buildStage(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDayTime(6000);
        player.setGameMode(GameType.SURVIVAL);
        for (int x = -16; x <= 16; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(new BlockPos(x, STAGE_Y - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y < 5; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAGE_Y + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // A pool three deep in the east of the stage, for the wardrobe's swimming shots.
        for (int x = 4; x <= 15; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = STAGE_Y - 4; y <= STAGE_Y - 1; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), y == STAGE_Y - 4
                            ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
                }
            }
        }
        player.teleportTo(level, START_X, STAGE_Y, 0.5, Set.of(), -90.0F, 0.0F, true);
    }
}
