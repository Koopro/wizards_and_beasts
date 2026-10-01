package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.screen.AdminControlCenterScreen;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSlider;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminToggle;
import com.mojang.logging.LogUtils;
import at.koopro.wizardsandbeasts.client.admin.viewer.EntityViewerScreen;
import at.koopro.wizardsandbeasts.client.heritage.gui.HeritageSelectionScreen;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
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
import java.util.List;

/**
 * Photographs the Control Center through a real client, so its layout is judged by looking at it.
 *
 * <p>Development only, and inert unless {@code WB_ADMIN_CAPTURE} names an output folder under
 * {@code screenshots/}. {@code WB_ADMIN_CAPTURE_WORLD} names the save to open (a disposable copy — steps change
 * spell state in it). The player must be an administrator — the {@code runClient} {@code Dev} account is on the
 * shipped allow-list. The panel is opened the way a player opens it, through the server, and each step drives it
 * only through its public widgets and methods, so what is photographed is what a player gets. The client quits
 * after the last shot.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class AdminScreenCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LOAD_TICKS = 100;
    private static final int SETTLE_TICKS = 15;
    private static final String OUT = System.getenv("WB_ADMIN_CAPTURE");
    private static final String STUPEFY = "wizards_and_beasts:stupefy";

    /** One photograph: what to do, how long to let it settle, then the file it is saved as. */
    private record Step(String name, Runnable action, int settleTicks) {
        Step(String name, Runnable action) {
            this(name, action, SETTLE_TICKS);
        }
    }

    private static List<Step> steps = List.of();
    private static int index = -1;
    private static int wait;
    private static boolean opened;
    private static boolean requested;
    private static boolean captureThisFrame;

    private AdminScreenCapture() {}

    private static boolean enabled() {
        return OUT != null && !FMLEnvironment.isProduction();
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!enabled() || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.guiScale().set(3);
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900);
        steps = plan();
        mc.createWorldOpenFlows().openWorld(System.getenv().getOrDefault("WB_ADMIN_CAPTURE_WORLD", "wb_admin_capture"),
                () -> LOGGER.warn("Admin capture: world did not open"));
    }

    private static List<Step> plan() {
        String plan = System.getenv("WB_ADMIN_CAPTURE_PLAN");
        return switch (plan == null ? "" : plan) {
            case "heritage" -> heritagePlan();
            case "creature" -> creaturePlan();
            case "brew" -> brewPlan();
            case "wand" -> wandPlan();
            case "visual" -> visualPlan();
            case "world" -> worldPlan();
            case "modules" -> modulesPlan();
            case "profiles" -> profilesPlan();
            case "players" -> playersPlan();
            case "ops" -> opsPlan();
            case "polish" -> polishPlan();
            default -> magicPlan();
        };
    }

    /**
     * Phase 13: the UX pass. Global search ("phoenix": results, keyboard choice, opening one), back navigation, a
     * setting tooltip, the save states (unsaved, refused, a dangerous change held for confirmation, saved), the
     * dashboard's recent changes and Undo, keyboard focus and Ctrl+Tab — then every section at GUI scale 2, 3 and 4.
     * Needs a disposable world copy {@code wb_admin_capture_polish}; Undo puts the one real change back.
     */
    private static List<Step> polishPlan() {
        String a = "admin.wizards_and_beasts.";
        List<Step> out = new java.util.ArrayList<>(List.of(
                new Step("u01_dashboard", () -> navigate(AdminCategory.DASHBOARD), 30),
                new Step("u02_search_empty", () -> screen().search(""), 40),
                new Step("u03_search_phoenix", () -> screen().search("phoenix"), 40),
                new Step("u03b_search_variants", () -> screen().search("niffler"), 20),
                new Step("u03c_search_phoenix_again", () -> screen().search("phoenix"), 10),
                new Step("u04_search_down_twice", () -> {
                    key(GLFW.GLFW_KEY_DOWN, 0);
                    key(GLFW.GLFW_KEY_DOWN, 0);
                }),
                new Step("u05_opened_result", () -> key(GLFW.GLFW_KEY_ENTER, 0), 40),
                new Step("u06_creature_from_search", () -> {
                    screen().search("phoenix");
                    key(GLFW.GLFW_KEY_ENTER, 0);
                }, 40),
                new Step("u07_back", () -> press(a + "button.back"), 30),
                new Step("u08_setting_tooltip", () -> {
                    screen().showTab(AdminCategory.TRAVEL, "rules");
                }, 20),
                new Step("u08b_setting_tooltip_hover", AdminScreenCapture::hoverFirstSliderLabel, 10),
                new Step("u09_unsaved", AdminScreenCapture::nudgeFirstSlider),
                new Step("u10_reverted", () -> press(a + "button.revert")),
                new Step("u11_refused", () -> {
                    screen().showTab(AdminCategory.MINISTRY, "rules");
                    AdminClientRequests.change(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                            "ministry_days_per_year"), "99999", false);
                }, 30),
                new Step("u12_dangerous_asks", () -> {
                    screen().showTab(AdminCategory.MAGIC, "rules");
                    AdminClientRequests.change(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                            "spell_damage_multiplier"), "2.5", false);
                }, 30),
                new Step("u13_dangerous_applied", () -> press(a + "button.apply"), 30),
                new Step("u14_dashboard_recent", () -> navigate(AdminCategory.DASHBOARD), 40),
                new Step("u15_undo_asks", () -> press(a + "dashboard.undo"), 20),
                new Step("u16_undone", () -> press(a + "dashboard.undo"), 50),
                new Step("u17_keyboard_focus", () -> {
                    screen().showTab(AdminCategory.WANDS, "rules");
                }, 20),
                new Step("u17b_keyboard_focus_tabbed", () -> {
                    key(GLFW.GLFW_KEY_TAB, 0);
                    key(GLFW.GLFW_KEY_TAB, 0);
                    key(GLFW.GLFW_KEY_TAB, 0);
                }),
                new Step("u18_ctrl_tab", () -> key(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL), 30)));
        for (int scale = 2; scale <= 4; scale++) {
            int s = scale;
            for (AdminCategory section : AdminCategory.values()) {
                out.add(new Step(String.format(java.util.Locale.ROOT, "s%d_%02d_%s", s, section.ordinal(), section.id()), () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.options.guiScale().get() != s) {
                        mc.options.guiScale().set(s);
                        mc.resizeDisplay();
                    }
                    navigate(section);
                }, 25));
            }
            out.add(new Step("s" + s + "_99_search", () -> screen().search("phoenix"), 25));
        }
        return out;
    }

    /**
     * Phase 12: Performance and Debug. Live metrics, a preset applied through its dialog and put back, debug tools
     * switched on (own and all-player debug mode, hitboxes), the live view, then the Control Center closed so the
     * world shot shows the hitboxes gone again (and the server log the leases released).
     */
    private static List<Step> opsPlan() {
        String p = "admin.wizards_and_beasts.perf.";
        String d = "admin.wizards_and_beasts.debug_tools.";
        return List.of(
                new Step("o01_perf_live", () -> screen().showTab(AdminCategory.PERFORMANCE, "live"), 50),
                new Step("o02_presets", () -> screen().showTab(AdminCategory.PERFORMANCE, "presets"), 30),
                new Step("o03_low_asks", () -> press(p + "preset.low"), 20),
                new Step("o04_low_applied", () -> press(p + "confirm.apply"), 40),
                new Step("o05_medium_asks", () -> press(p + "preset.medium"), 20),
                new Step("o06_medium_applied", () -> press(p + "confirm.apply"), 40),
                new Step("o07_perf_settings", () -> screen().showTab(AdminCategory.PERFORMANCE, "settings"), 30),
                new Step("o08_debug_tools", () -> screen().showTab(AdminCategory.DEBUG, "tools"), 50),
                new Step("o09_my_debug_on", () -> press(d + "turn_on"), 40),
                new Step("o10_all_debug_on", () -> press(d + "turn_on"), 40),
                // Flip the hitbox overlay from whatever this client had; the world shot after closing shows it back.
                new Step("o11_hitboxes_flipped", () -> {
                    boolean before = at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState.hitboxes();
                    LOGGER.info("Admin capture: hitboxes were {}", before ? "on" : "off");
                    at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState.setHitboxes(!before);
                    screen().showTab(AdminCategory.DEBUG, "tools");
                }, 40),
                new Step("o12_debug_live", () -> screen().showTab(AdminCategory.DEBUG, "live"), 50),
                new Step("o13_closed_world", () -> {
                    Minecraft.getInstance().setScreen(null);
                    LOGGER.info("Admin capture: after closing, hitboxes are {}",
                            at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState.hitboxes() ? "on" : "off");
                }, 60));
    }

    /**
     * Phase 11: the Players section on the capturing player. Every tab, a spell granted past the rules and a deposit,
     * each through its confirmation dialog, an effect previewed on the administrator, then the action log.
     */
    private static List<Step> playersPlan() {
        String p = "admin.wizards_and_beasts.players.";
        return List.of(
                new Step("q01_overview", () -> screen().showPlayers("overview", me()), 40),
                new Step("q02_heritage", () -> screen().showPlayers("heritage", me()), 30),
                new Step("q03_skills", () -> screen().showPlayers("skills", me()), 30),
                new Step("q04_spells", () -> screen().showPlayers("spells", me()), 30),
                new Step("q05_revoke_asks", () -> press(p + "button.item.spell_revoke"), 20),
                new Step("q06_revoked", () -> press(p + "action.spell_revoke"), 40),
                new Step("q06b_grant_asks", () -> press(p + "button.spell_grant"), 20),
                new Step("q06c_granted", () -> press(p + "action.spell_grant"), 40),
                new Step("q07_effects_preview", () -> {
                    screen().showPlayers("effects", me());
                }, 30),
                new Step("q08_previewed", () -> press(p + "button.effect_preview"), 40),
                new Step("q09_ministry", () -> screen().showPlayers("ministry", me()), 30),
                new Step("q10_economy", () -> screen().showPlayers("economy", me()), 30),
                new Step("q11_deposit_asks", () -> press(p + "button.money_deposit"), 20),
                new Step("q12_deposited", () -> press(p + "action.money_deposit"), 40),
                new Step("q13_debug", () -> screen().showPlayers("debug", me()), 30),
                new Step("q14_log", () -> screen().showPlayers("log", me()), 40));
    }

    /**
     * Phase 10: the Profiles section. Previews, the apply confirmation (cancelled, then accepted), a snapshot, the
     * history with a group revert, and imports of a malformed and an incompatible file placed in the profile folder.
     */
    private static List<Step> profilesPlan() {
        return List.of(
                new Step("p01_current", () -> screen().showProfiles("profiles", "@current"), 40),
                new Step("p02_hardcore_preview", () -> screen().showProfiles("profiles", "hardcore"), 40),
                new Step("p03_apply_asks", () -> press("admin.wizards_and_beasts.profiles.button.apply"), 20),
                new Step("p04_cancelled", () -> press("admin.wizards_and_beasts.button.cancel"), 10),
                new Step("p05_sandbox_preview", () -> screen().showProfiles("profiles", "sandbox"), 40),
                new Step("p06_snapshots", () -> screen().showProfiles("snapshots", "@current"), 30),
                new Step("p07_snapshot_taken", () -> press("admin.wizards_and_beasts.profiles.button.create_snapshot"), 40),
                new Step("p08_hardcore_apply_dialog", () -> screen().showProfiles("profiles", "hardcore"), 40),
                new Step("p09_hardcore_confirm", () -> press("admin.wizards_and_beasts.profiles.button.apply"), 20),
                new Step("p10_hardcore_applied", () -> press("admin.wizards_and_beasts.profiles.button.apply_confirm"), 60),
                new Step("p11_history", () -> screen().showProfiles("history", "@newest"), 30),
                new Step("p12_revert_group_asks", () -> press("admin.wizards_and_beasts.profiles.button.revert_group"), 20),
                new Step("p13_group_reverted", () -> press("admin.wizards_and_beasts.profiles.button.revert"), 60),
                new Step("p14_import_about", () -> screen().showProfiles("import", "@about"), 30),
                new Step("p15_import_broken", () -> screen().showProfiles("import", "broken.json"), 20),
                new Step("p16_import_broken_result", () -> press("admin.wizards_and_beasts.profiles.button.import"), 40),
                new Step("p17_import_future", () -> screen().showProfiles("import", "future.json"), 20),
                new Step("p18_import_future_result", () -> press("admin.wizards_and_beasts.profiles.button.import"), 40),
                new Step("p19_import_usable", () -> screen().showProfiles("import", "usable.json"), 20),
                new Step("p20_import_usable_result", () -> press("admin.wizards_and_beasts.profiles.button.import"), 40),
                new Step("p21_imported_preview", () -> screen().showProfiles("profiles", "event_night"), 40));
    }

    /** Phase 9: the Modules section. The disable press stops at the server's confirmation dialog, then cancels. */
    private static List<Step> modulesPlan() {
        return List.of(
                new Step("m01_apparition", () -> screen().showModule("apparition"), 30),
                new Step("m02_player_abilities", () -> screen().showModule("player_abilities"), 20),
                new Step("m03_disable_asks", () -> press("admin.wizards_and_beasts.module_page.disable"), 30),
                new Step("m04_cancelled", () -> press("admin.wizards_and_beasts.button.cancel"), 10),
                new Step("m05_ministry_partial", () -> screen().showModule("ministry"), 20),
                new Step("m06_azkaban_new_chunks", () -> screen().showModule("azkaban"), 20),
                new Step("m07_owls_list_scrolled", () -> {
                    screen().showModule("owls");
                    screen().mouseScrolled(screen().width * 0.35, screen().height * 0.6, 0, -10);
                }, 20),
                new Step("m08_profiles", () -> screen().showTab(AdminCategory.MODULES, "profiles"), 30));
    }

    /** Phase 8: Travel (Floo, Apparition), Ministry, Economy and World. */
    private static List<Step> worldPlan() {
        return List.of(
                new Step("x01_travel_floo", () -> screen().showTab(AdminCategory.TRAVEL, "floo"), 30),
                new Step("x02_travel_apparition", () -> screen().showTab(AdminCategory.TRAVEL, "apparition"), 20),
                new Step("x03_travel_rules", () -> screen().showTab(AdminCategory.TRAVEL, "rules"), 20),
                new Step("x04_ministry_rules", () -> screen().showTab(AdminCategory.MINISTRY, "rules"), 30),
                new Step("x05_ministry_law", () -> screen().showTab(AdminCategory.MINISTRY, "law"), 20),
                new Step("x06_ministry_law_scrolled", () -> screen().mouseScrolled(
                        screen().width * 0.6, screen().height * 0.6, 0, -12), 10),
                new Step("x07_economy_gringotts", () -> screen().showTab(AdminCategory.ECONOMY, "gringotts"), 30),
                new Step("x08_economy_rules", () -> screen().showTab(AdminCategory.ECONOMY, "rules"), 20),
                new Step("x09_world_overview", () -> screen().showTab(AdminCategory.WORLD, "overview"), 30),
                new Step("x10_world_overview_scrolled", () -> screen().mouseScrolled(
                        screen().width * 0.6, screen().height * 0.6, 0, -15), 10),
                new Step("x11_world_rules", () -> screen().showTab(AdminCategory.WORLD, "rules"), 20),
                new Step("x12_world_rules_scrolled", () -> screen().mouseScrolled(
                        screen().width * 0.6, screen().height * 0.6, 0, -6), 10));
    }

    /** Phase 7: the Visuals section. The log lines prove the in-world preview stops when the page goes. */
    private static List<Step> visualPlan() {
        return List.of(
                new Step("v01_beams_crucio", () -> screen().showVisuals("beams", "crucio"), 40),
                new Step("v02_look_rows", () -> screen().mouseScrolled(screen().width * 0.75, screen().height * 0.5, 0, -11)),
                new Step("v03_edited_draft", () -> {
                    nudgeFirstSlider();
                    nudgeFirstSlider();
                }, 10),
                new Step("v04_edited_preview", () -> screen().mouseScrolled(screen().width * 0.75, screen().height * 0.5, 0, 20), 10),
                new Step("v04b_presets", () -> screen().mouseScrolled(screen().width * 0.75, screen().height * 0.5, 0, -4), 10),
                new Step("v05_reverted", () -> press("admin.wizards_and_beasts.button.revert"), 10),
                new Step("v06_avada_no_compare", () -> {
                    screen().showVisuals("beams", "avada_kedavra");
                    toggle("admin.wizards_and_beasts.beam.compare");
                }, 30),
                new Step("v07_leviosa_off", () -> {
                    toggle("admin.wizards_and_beasts.beam.compare");
                    screen().showVisuals("beams", "wingardium_leviosa");
                }, 30),
                new Step("v08_watch_in_world", () -> {
                    screen().showVisuals("beams", "aguamenti");
                    press("admin.wizards_and_beasts.beam.watch");
                }, 30),
                // The peek hides the panel for five seconds; the preview keeps running while the page is open.
                new Step("v08b_back_from_peek", () -> { }, 90),
                new Step("v09_impacts", () -> {
                    LOGGER.info("Admin capture: preview running before leaving the beam tab: {}",
                            at.koopro.wizardsandbeasts.client.beam.BeamChannelClient.previewRunning());
                    screen().showVisuals("impacts", "");
                    LOGGER.info("Admin capture: preview running after leaving the beam tab: {}",
                            at.koopro.wizardsandbeasts.client.beam.BeamChannelClient.previewRunning());
                }, 30),
                new Step("v10_hud", () -> screen().showVisuals("hud", ""), 30),
                new Step("v11_screen", () -> screen().showVisuals("screen", ""), 20),
                new Step("v12_particles", () -> screen().showVisuals("particles", ""), 20),
                new Step("v13_entities", () -> screen().showVisuals("entities", ""), 30),
                new Step("v14_debug", () -> screen().showVisuals("debug", ""), 20));
    }

    /** Phase 6: the Wands and Travel sections. */
    private static List<Step> wandPlan() {
        return List.of(
                new Step("w01_holly_page", () -> screen().showWand("woods", "wizards_and_beasts:holly"), 40),
                new Step("w02_holly_pairings", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -6)),
                new Step("w03_core_page", () -> screen().showWand("cores", "wizards_and_beasts:phoenix_feather"), 30),
                new Step("w04_generator_valid", () -> screen().showWandMake("wizards_and_beasts:elder",
                        "wizards_and_beasts:thestral_tail_hair", 15.0f, "SUPPLE", ""), 40),
                new Step("w05_generator_preset", () -> screen().showWandMake("wizards_and_beasts:holly",
                        "wizards_and_beasts:phoenix_feather", 11.0f, "SUPPLE", "wizards_and_beasts:harry"), 40),
                new Step("w06_generator_no_recipe", () -> screen().showWandMake("wizards_and_beasts:holly",
                        "wizards_and_beasts:rougarou_hair", 11.0f, "RIGID", ""), 40),
                new Step("w07_wand_rules", () -> screen().showWand("rules", ""), 25),
                new Step("w08_broom_page", () -> screen().showBroom("wizards_and_beasts:firebolt", "brooms"), 40),
                new Step("w09_broom_sliders", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -8)),
                new Step("w10_school_broom", () -> screen().showBroom("wizards_and_beasts:broom", "brooms"), 30),
                new Step("w11_travel_rules", () -> screen().showBroom("wizards_and_beasts:broom", "rules"), 25));
    }

    /** Phase 5: the Brewing section. */
    private static List<Step> brewPlan() {
        return List.of(
                new Step("p01_pepperup_page", () -> screen().showBrew("wizards_and_beasts:pepperup_potion", "brews"), 30),
                new Step("p02_effect_added_draft", () -> press("admin.wizards_and_beasts.effect_editor.add"), 10),
                // Adding an effect the brew never had: the server must hold it for confirmation.
                new Step("p03_server_asks_to_confirm", () -> press("admin.wizards_and_beasts.button.apply"), 30),
                new Step("p04_after_cancel", () -> {
                    press("admin.wizards_and_beasts.button.cancel");
                    press("admin.wizards_and_beasts.button.revert");
                }, 10),
                new Step("p05_pepperup_recipe", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -10)),
                new Step("p06_felix_components", () -> screen().showBrew("wizards_and_beasts:felix_felicis", "brews"), 30),
                new Step("p07_filter_effect", () -> stepSelector(0, 3), 10),
                new Step("p08_rules_tab", () -> screen().showBrew("wizards_and_beasts:pepperup_potion", "rules"), 25));
    }

    /** Phase 4: the Creature Lab and the Entity Viewer. */
    private static List<Step> creaturePlan() {
        return List.of(
                new Step("c01_creatures_hippogriff", () -> screen().showCreature("hippogriff", "creatures"), 30),
                new Step("c02_hippogriff_variants", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -12)),
                new Step("c03_acromantula_spawning", () -> {
                    screen().showCreature("acromantula", "creatures");
                }, 30),
                new Step("c04_acromantula_scrolled", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -5)),
                new Step("c05_rules_tab", () -> screen().showCreature("hippogriff", "rules"), 25),
                new Step("c06_hippogriff_page", () -> screen().showCreature("hippogriff", "creatures"), 25),
                new Step("c06b_viewer_hippogriff", () -> press("admin.wizards_and_beasts.creature.open_viewer"), 30),
                new Step("c07_viewer_overlays", () -> {
                    toggle("admin.wizards_and_beasts.viewer.hitbox");
                    toggle("admin.wizards_and_beasts.viewer.name");
                    toggle("admin.wizards_and_beasts.viewer.bones");
                    toggle("admin.wizards_and_beasts.viewer.debug");
                    if (Minecraft.getInstance().screen instanceof EntityViewerScreen viewer) {
                        viewer.orbit(70.0f, -15.0f);
                    }
                    stepSelector(0, 2); // variant: two coats on
                }, 15),
                new Step("c08_viewer_clip", () -> stepSelector(1, 3), 30),
                new Step("c09_test_spawn", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen != null) {
                        mc.screen.onClose();
                    }
                }, 5),
                new Step("c10_spawned_peek", () -> {
                    screen().showCreature("hippogriff", "creatures");
                    press("admin.wizards_and_beasts.creature.spawn");
                }, 20),
                new Step("c11_cleaned_up", () -> press("admin.wizards_and_beasts.creature.cleanup"), 45));
    }

    /** Presses a checkbox on whatever admin screen is showing. */
    private static void toggle(String labelKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            return;
        }
        String label = Component.translatable(labelKey).getString();
        for (GuiEventListener child : mc.screen.children()) {
            if (child instanceof at.koopro.wizardsandbeasts.client.admin.widget.AdminCheckbox box
                    && box.getMessage().getString().equals(label)) {
                box.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
        }
    }

    /** Steps the {@code index}-th selector on the showing screen forward {@code times} times. */
    private static void stepSelector(int index, int times) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            return;
        }
        int seen = 0;
        for (GuiEventListener child : mc.screen.children()) {
            if (child instanceof at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector selector) {
                if (seen++ == index) {
                    selector.setFocused(true);
                    for (int i = 0; i < times; i++) {
                        selector.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0));
                    }
                    return;
                }
            }
        }
    }

    /** Phase 3: the Heritages section. */
    private static List<Step> heritagePlan() {
        return List.of(
                new Step("h01_heritages_wizardkind", () -> screen().showHeritage(Heritage.WIZARDKIND, "heritages"), 25),
                new Step("h02_wizardkind_preview_scrolled", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -6)),
                new Step("h03_veela_transform_rule", () -> screen().showHeritage(Heritage.VEELA, "heritages"), 25),
                new Step("h04_goblin_open_draft", () -> {
                    screen().showHeritage(Heritage.GOBLIN, "heritages");
                    toggleFirst();
                }, 10),
                // Opening an unfinished heritage: the server must hold it for confirmation.
                new Step("h05_server_asks_to_confirm", () -> press("admin.wizards_and_beasts.button.apply"), 30),
                new Step("h06_after_cancel", () -> {
                    press("admin.wizards_and_beasts.button.cancel");
                    press("admin.wizards_and_beasts.button.revert");
                }),
                new Step("h07_rules_tab", () -> screen().showHeritage(Heritage.WIZARDKIND, "rules"), 25),
                new Step("h08_players_tab", () -> screen().showHeritage(Heritage.WIZARDKIND, "players"), 25),
                new Step("h09_player_inspected", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        screen().showPlayer(mc.player.getUUID());
                    }
                }, 30),
                new Step("h10_reset_confirm_dialog", () -> press("admin.wizards_and_beasts.heritage_players.reset"), 10),
                new Step("h11_onboarding_preview", () -> {
                    press("admin.wizards_and_beasts.button.cancel");
                    screen().showHeritage(Heritage.WIZARDKIND, "heritages");
                    press("admin.wizards_and_beasts.heritage.preview_onboarding");
                }, 25),
                new Step("h12_back_from_preview", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen != null) {
                        mc.screen.onClose();
                    }
                }, 15));
    }

    private static List<Step> magicPlan() {
        return List.of(
                new Step("01_dashboard", () -> navigate(AdminCategory.DASHBOARD)),
                new Step("02_magic_rules", () -> {
                    navigate(AdminCategory.MAGIC);
                    press("admin.wizards_and_beasts.tab.rules");
                }),
                new Step("03_magic_rules_edited", () -> {
                    // Spell damage ×3: past the "more than double" line, so the server will ask.
                    nudgeFirstSlider();
                    nudgeFirstSlider();
                }),
                new Step("04_server_asks_to_confirm", () -> press("admin.wizards_and_beasts.button.apply"), 25),
                new Step("05_after_cancel", () -> press("admin.wizards_and_beasts.button.cancel")),
                new Step("06_spells_browser", () -> {
                    press("admin.wizards_and_beasts.button.revert");
                    screen().showSpell(STUPEFY);
                }, 25),
                new Step("07_spell_detail_scrolled", () -> screen().mouseScrolled(
                        screen().width * 0.75, screen().height * 0.5, 0, -6)),
                new Step("08_spell_toggled_draft", () -> {
                    screen().mouseScrolled(screen().width * 0.75, screen().height * 0.5, 0, 20);
                    toggleFirst();
                }),
                // Apply turns active on the tick after an edit, as it does for a player.
                new Step("08b_spell_disabled_applied", () -> press("admin.wizards_and_beasts.button.apply"), 30),
                new Step("09_dark_arts", () -> navigate(AdminCategory.DARK_ARTS), 25),
                new Step("10_preview_peek", () -> {
                    screen().showSpell(STUPEFY);
                    press("admin.wizards_and_beasts.button.preview");
                }, 6),
                new Step("11_test_refused_no_target", () -> {
                    // Looking at nothing: the server must say it found no target, not invent one.
                    press("admin.wizards_and_beasts.button.test");
                }, 60),
                new Step("12_ministry_rejected", () -> {
                    navigate(AdminCategory.MINISTRY);
                    // A request no widget could produce: the server must refuse it and the row must say so.
                    AdminClientRequests.change(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                            "ministry_days_per_year"), "99999", false);
                }),
                new Step("13_narrow_gui_scale", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                    screen().showSpell(STUPEFY);
                }, 25));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled() || !opened || captureThisFrame) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!requested) {
            if (++wait < LOAD_TICKS) {
                return;
            }
            requested = true;
            wait = 0;
            AdminClientRequests.open();
            return;
        }
        // The onboarding preview is a screen the Control Center opens itself; a step may be looking at it.
        boolean preview = index >= 0 && (mc.screen instanceof HeritageSelectionScreen selection && selection.isPreview()
                || mc.screen instanceof EntityViewerScreen);
        // A step named *_world closes the Control Center on purpose and photographs the world behind it.
        boolean worldStep = index >= 0 && index < steps.size() && steps.get(index).name().endsWith("_world");
        if (!(mc.screen instanceof AdminControlCenterScreen) && !preview && !worldStep) {
            if (++wait > 200) {
                LOGGER.error("Admin capture: the Control Center never opened (is the player an admin?) — screen is {}",
                        mc.screen == null ? "none" : mc.screen.getClass().getName());
                mc.stop();
            }
            return;
        }
        if (index < 0) {
            index = 0;
            steps.get(0).action().run();
            wait = steps.get(0).settleTicks();
            return;
        }
        if (--wait > 0) {
            return;
        }
        captureThisFrame = true;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!captureThisFrame) {
            return;
        }
        captureThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        Path file = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve(OUT)
                .resolve(steps.get(index).name() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Admin capture: cannot write {}", file, e);
            }
        }));
        index++;
        if (index >= steps.size()) {
            LOGGER.info("Admin capture: done, {} shots", steps.size());
            mc.stop();
            return;
        }
        if (mc.screen instanceof AdminControlCenterScreen || mc.screen instanceof EntityViewerScreen
                || mc.screen instanceof HeritageSelectionScreen selection && selection.isPreview()) {
            steps.get(index).action().run();
        }
        wait = steps.get(index).settleTicks();
    }

    // ── driving the screen through its widgets ──

    /** The capturing player's UUID, read when a step runs (the plan is built before the world is open). */
    private static @org.jspecify.annotations.Nullable String me() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID().toString();
    }

    private static AdminControlCenterScreen screen() {
        return (AdminControlCenterScreen) Minecraft.getInstance().screen;
    }

    private static void navigate(AdminCategory section) {
        if (Minecraft.getInstance().screen instanceof AdminControlCenterScreen screen) {
            screen.navigate(section);
        }
    }

    private static void nudgeFirstSlider() {
        if (!(Minecraft.getInstance().screen instanceof AdminControlCenterScreen screen)) {
            return;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AdminSlider slider && slider.active && slider.visible) {
                slider.setFocused(true);
                slider.keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT));
                return;
            }
        }
    }

    private static void key(int key, int modifiers) {
        if (Minecraft.getInstance().screen instanceof AdminControlCenterScreen screen) {
            Minecraft.getInstance().setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            screen.keyPressed(new KeyEvent(key, 0, modifiers));
        }
    }

    /** Moves the real pointer over the label of the first visible slider's row, so its tooltip draws. */
    private static void hoverFirstSliderLabel() {
        if (!(Minecraft.getInstance().screen instanceof AdminControlCenterScreen screen)) {
            return;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AdminSlider slider && slider.visible) {
                Minecraft mc = Minecraft.getInstance();
                double scale = mc.getWindow().getGuiScale();
                GLFW.glfwSetCursorPos(mc.getWindow().handle(), (slider.getX() - 60) * scale, (slider.getY() + 4) * scale);
                return;
            }
        }
    }

    private static void toggleFirst() {
        if (!(Minecraft.getInstance().screen instanceof AdminControlCenterScreen screen)) {
            return;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AdminToggle toggle && toggle.active && toggle.visible) {
                toggle.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
        }
    }

    private static void press(String labelKey) {
        if (!(Minecraft.getInstance().screen instanceof AdminControlCenterScreen screen)) {
            return;
        }
        String label = Component.translatable(labelKey).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AdminButton button && button.active && button.getMessage().getString().equals(label)) {
                button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
        }
    }
}
