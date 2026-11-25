package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
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
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs the look previews (docs/development/LOOK_PREVIEW_REFERENCE.md): the Quidditch balls, Norbert's egg in a
 * fire, and the Dark Mark over the trees at night. Development only, inert unless {@code WB_LOOK_PREVIEW=1};
 * {@code WB_LOOK_PREVIEW_WORLD} names the save. Shots go to {@code screenshots/look_preview}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class LookPreviewCapture {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = System.getenv("WB_LOOK_PREVIEW") != null && !FMLEnvironment.isProduction();

    private record Step(String shot, int ticks, Consumer<ServerPlayer> server) {}

    private static List<Step> script() {
        List<Step> steps = new java.util.ArrayList<>();
        steps.add(new Step("", 100, LookPreviewCapture::stage));
        // WB_LOOK_ONLY=gnome: just the signature-rig sheet in hand (systems audit A1), not the whole sweep.
        if ("gnome".equals(System.getenv("WB_LOOK_ONLY"))) {
            steps.addAll(GNOME_SCENES);
            return steps;
        }
        if ("baby_niffler".equals(System.getenv("WB_LOOK_ONLY"))) {
            steps.addAll(BABY_NIFFLER_SCENES);
            return steps;
        }
        if ("kappa".equals(System.getenv("WB_LOOK_ONLY"))) {
            steps.addAll(KAPPA_SCENES);
            return steps;
        }
        if ("qilin".equals(System.getenv("WB_LOOK_ONLY"))) {
            steps.addAll(QILIN_SCENES);
            return steps;
        }
        if ("zouwu".equals(System.getenv("WB_LOOK_ONLY"))) {
            steps.addAll(ZOUWU_SCENES);
            return steps;
        }
        // The Quidditch balls in vanilla style: a hero shot, then four views of each ball.
        steps.add(new Step("", 40, p -> eye(p, QX + 1.9, 1.95, -1.2, 0f, 4f)));
        steps.add(new Step("quidditch_hero", 10, p -> {}));
        for (int b = 0; b < BALLS.length; b++) {
            for (int v = 0; v < VIEWS.length; v++) {
                double x = viewX(b, v);
                double back = VIEW_BACK[b];
                // A three-quarter look from up and to one side.
                steps.add(new Step("", 30, p -> eyeAt(p, x - 0.45 * back, VIEW_Y + 0.55 * back, VIEW_Z - back,
                        x, VIEW_Y, VIEW_Z)));
                steps.add(new Step(BALLS[b] + "_" + VIEWS[v], 10, p -> {}));
            }
        }
        // Held as vanilla holds a snowball: the flat sprite in hand, the hotbar showing the icons.
        String[] held = {"quaffle", "bludger", "golden_snitch", "norwegian_ridgeback_egg"};
        steps.add(new Step("", 40, p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.getInventory().clearContent();
            for (int i = 0; i < held.length; i++) {
                p.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, held[i]))));
            }
            p.getInventory().setItem(held.length, new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beaters_bat"))));
            eye(p, QX + 1.9, 1.95, -1.2, 0f, 4f);
            hold(p, 0);
            showGui = true;
        }));
        for (int i = 0; i < held.length; i++) {
            int slot = i;
            if (i > 0) {
                steps.add(new Step("", 20, p -> hold(p, slot)));
            }
            steps.add(new Step("hand_" + held[i], 10, p -> {}));
        }
        steps.add(new Step("", 10, p -> {
            showGui = false;
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        }));
        steps.addAll(OTHER_SCENES);
        steps.addAll(GNOME_SCENES);
        return steps;
    }

    /** Where the gnome's garden starts, east of the humanoid corridor. */
    private static final double GX = 54.5;

    /**
     * The garden gnome (A1), Mojang style with its own jaw and ears: in the Burrow's weedy garden by a hedge
     * (Chamber of Secrets ch.3), a person beside it for scale, then front, side and three-quarter close-ups.
     */
    private static final List<Step> GNOME_SCENES = List.of(
            new Step("", 40, p -> eyeAt(p, GX + 1.2, 1.7, -3.2, GX + 1.2, 0.8, 0.5)),
            new Step("gnome_garden", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, GX, 0.55, -0.9, GX, 0.25, 0.5)),
            new Step("gnome_front", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, GX + 1.4, 0.55, 0.5, GX, 0.25, 0.5)),
            new Step("gnome_side", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, GX + 0.9, 0.85, -0.6, GX, 0.25, 0.5)),
            new Step("gnome_three_quarter", 10, p -> {}));

    /** Where the Niffler family sits, east of the gnome's garden. */
    private static final double NX = 66.5;

    /**
     * The Baby Niffler (A1), its own rig at the adult's texel size: three babies in three coats around an adult on a
     * scatter of gold (Crimes of Grindelwald: the babies tumbling over Newt's desk), a person for scale, then front,
     * side and three-quarter close-ups of the classic black baby.
     */
    private static final List<Step> BABY_NIFFLER_SCENES = List.of(
            new Step("", 40, p -> eyeAt(p, NX + 0.5, 1.4, -2.8, NX + 0.5, 0.3, 1.0)),
            new Step("baby_niffler_family", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, NX, 0.3, -0.55, NX, 0.12, 0.5)),
            new Step("baby_niffler_front", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, NX + 0.9, 0.3, 0.5, NX, 0.12, 0.5)),
            new Step("baby_niffler_side", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, NX + 0.6, 0.55, -0.45, NX, 0.12, 0.5)),
            new Step("baby_niffler_three_quarter", 10, p -> {}));

    /** Where the Kappa's pond is, east of the Niffler family. */
    private static final double KX = 80.5;

    /**
     * The Kappa (A1), Mojang style with the dish as geometry: on the bank of a shallow pond (Prisoner of Azkaban
     * ch.8, the water-dwellers of Lupin's lessons), a person for scale, then front, side, three-quarter and a look
     * down into the water in its head.
     */
    private static final List<Step> KAPPA_SCENES = List.of(
            new Step("", 40, p -> eyeAt(p, KX + 0.8, 2.0, -3.4, KX + 0.8, 0.8, 0.8)),
            new Step("kappa_pond", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, KX, 1.0, -1.7, KX, 0.75, 0.5)),
            new Step("kappa_front", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, KX + 2.1, 0.9, 0.5, KX, 0.75, 0.5)),
            new Step("kappa_side", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, KX + 1.3, 1.6, -1.2, KX, 0.8, 0.5)),
            new Step("kappa_three_quarter", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, KX + 0.2, 2.1, -0.5, KX, 1.15, 0.45)),
            new Step("kappa_dish", 10, p -> {}));

    /** Where the Qilin stands, east of the Kappa's pond. */
    private static final double QLX = 95.5;

    /**
     * The Qilin (A1), Mojang style, the film's golden equine dragon (The Secrets of Dumbledore): in a meadow with a
     * rare pale one behind and a person for scale, then front, side, three-quarter and its head close up.
     */
    private static final List<Step> QILIN_SCENES = List.of(
            new Step("", 40, p -> eyeAt(p, QLX + 0.6, 2.2, -4.2, QLX + 0.6, 0.9, 1.0)),
            new Step("qilin_meadow", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, QLX, 1.3, -2.4, QLX, 0.9, 0.5)),
            new Step("qilin_front", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, QLX + 2.8, 1.0, 0.3, QLX, 0.8, 0.3)),
            new Step("qilin_side", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, QLX + 1.8, 1.7, -1.8, QLX, 0.9, 0.4)),
            new Step("qilin_three_quarter", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, QLX + 0.7, 1.35, -1.4, QLX, 1.15, -0.2)),
            new Step("qilin_head", 10, p -> {}));

    /** Where the Zouwu stands, well east of the Qilin: it is elephant-sized and its tail is longer still. */
    private static final double ZX = 118.5;

    /**
     * The Zouwu (A1), Mojang style (The Crimes of Grindelwald): on open ground with a person for scale, then front,
     * side (the whole tail), three-quarter and the face in its mane.
     */
    private static final List<Step> ZOUWU_SCENES = List.of(
            new Step("", 40, p -> eyeAt(p, ZX + 7.5, 5.5, -12.5, ZX + 0.5, 2.0, 2.0)),
            new Step("zouwu_scale", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, ZX, 3.2, -9.5, ZX, 2.6, 0.0)),
            new Step("zouwu_front", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, ZX + 14.0, 3.0, 2.5, ZX, 2.3, 2.5)),
            new Step("zouwu_side", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, ZX + 6.5, 4.5, -7.0, ZX, 2.4, 0.5)),
            new Step("zouwu_three_quarter", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, ZX + 1.5, 3.3, -5.4, ZX, 3.0, -2.0)),
            new Step("zouwu_face", 10, p -> {}));

    private static void zouwuGround(ServerLevel level) {
        for (int x = 106; x <= 130; x++) {
            for (int z = -14; z <= 10; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y <= 4; y++) {
                    if (!level.getBlockState(base.offset(x, y, z)).isAir()) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        command(level, "kill @e[type=wizards_and_beasts:zouwu]");
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "zouwu"));
        if (type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                instanceof net.minecraft.world.entity.Mob zouwu) {
            zouwu.snapTo(base.getX() + ZX, base.getY(), base.getZ() + 0.5, 180f, 0f);
            zouwu.setYHeadRot(180f);
            zouwu.setYBodyRot(180f);
            zouwu.setNoAi(true);
            zouwu.setPersistenceRequired();
            level.addFreshEntity(zouwu);
        }
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[180f,0f],immovable:1b,hide_description:1b}",
                base.getX() + ZX - 4.2, (double) base.getY(), base.getZ() - 1.5));
    }

    private static void qilinMeadow(ServerLevel level) {
        for (int x = 91; x <= 101; x++) {
            for (int z = -6; z <= 5; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base.offset(93, 0, 3), Blocks.TALL_GRASS.defaultBlockState());
        level.setBlockAndUpdate(base.offset(98, 0, 4), Blocks.OXEYE_DAISY.defaultBlockState());
        command(level, "kill @e[type=wizards_and_beasts:qilin]");
        spawnQilin(level, QLX, 0.5, 180f, null);
        spawnQilin(level, QLX - 2.2, 3.2, 150f, "pale");
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[180f,0f],immovable:1b,hide_description:1b}",
                base.getX() + QLX + 2.2, (double) base.getY(), base.getZ() + 1.0));
    }

    private static void spawnQilin(ServerLevel level, double x, double z, float yaw, @org.jspecify.annotations.Nullable String coat) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "qilin"));
        if (type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                instanceof net.minecraft.world.entity.Mob qilin) {
            qilin.snapTo(base.getX() + x, base.getY(), base.getZ() + z, yaw, 0f);
            qilin.setYHeadRot(yaw);
            qilin.setYBodyRot(yaw);
            qilin.setNoAi(true);
            qilin.setPersistenceRequired();
            if (qilin instanceof at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity beast) {
                beast.applyVariant(coat == null ? "base" : coat);
            }
            level.addFreshEntity(qilin);
        }
    }

    private static void kappaPond(ServerLevel level) {
        for (int x = 76; x <= 85; x++) {
            for (int z = -5; z <= 4; z++) {
                boolean pond = x >= 81 && x <= 84 && z >= 1 && z <= 3;
                level.setBlockAndUpdate(base.offset(x, -1, z), pond ? Blocks.WATER.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState());
                if (pond) {
                    level.setBlockAndUpdate(base.offset(x, -2, z), Blocks.MUD.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(base.offset(80, 0, 3), Blocks.TALL_GRASS.defaultBlockState());
        command(level, "kill @e[type=wizards_and_beasts:kappa]");
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "kappa"));
        if (type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                instanceof net.minecraft.world.entity.Mob kappa) {
            kappa.snapTo(base.getX() + KX, base.getY(), base.getZ() + 0.5, 180f, 0f);
            kappa.setYHeadRot(180f);
            kappa.setYBodyRot(180f);
            kappa.setNoAi(true);
            kappa.setPersistenceRequired();
            level.addFreshEntity(kappa);
        }
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[180f,0f],immovable:1b,hide_description:1b}",
                base.getX() + KX - 1.6, (double) base.getY(), base.getZ() + 0.8));
    }

    private static void nifflerFamily(ServerLevel level) {
        for (int x = 63; x <= 71; x++) {
            for (int z = -5; z <= 3; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base.offset(68, 0, 2), Blocks.GOLD_BLOCK.defaultBlockState());
        // The adult behind, the babies in front: classic black (the close-ups), grey and dark brown.
        // The preview world is kept between runs: last run's family goes first.
        command(level, "kill @e[type=wizards_and_beasts:baby_niffler]");
        command(level, "kill @e[type=wizards_and_beasts:niffler]");
        spawnNiffler(level, "niffler", NX - 1.3, 1.2, 160f, at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity.Coat.CLASSIC);
        spawnNiffler(level, "baby_niffler", NX, 0.5, 180f, at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity.Coat.CLASSIC);
        spawnNiffler(level, "baby_niffler", NX + 1.6, 1.6, 150f, at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity.Coat.GREY);
        spawnNiffler(level, "baby_niffler", NX - 0.5, 2.2, 210f, at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity.Coat.DARK_BROWN);
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[180f,0f],immovable:1b,hide_description:1b}",
                base.getX() + NX + 2.8, (double) base.getY(), base.getZ() + 0.6));
    }

    private static void spawnNiffler(ServerLevel level, String id, double x, double z, float yaw,
                                     at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity.Coat coat) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
        if (type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                instanceof at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity niffler) {
            niffler.snapTo(base.getX() + x, base.getY(), base.getZ() + z, yaw, 0f);
            niffler.setYHeadRot(yaw);
            niffler.setYBodyRot(yaw);
            niffler.setNoAi(true);
            niffler.setCoat(coat);
            level.addFreshEntity(niffler);
        }
    }

    private static void gnomeGarden(ServerLevel level) {
        for (int x = 51; x <= 60; x++) {
            for (int z = -5; z <= 3; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
            // The hedge the gnomes are flung over, and weeds along it.
            level.setBlockAndUpdate(base.offset(x, 0, 2), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            level.setBlockAndUpdate(base.offset(x, 1, 2), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            if (x % 2 == 0) {
                level.setBlockAndUpdate(base.offset(x, 0, 1), Blocks.SHORT_GRASS.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base.offset(56, 0, 0), Blocks.DANDELION.defaultBlockState());
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gnome"));
        if (type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                instanceof net.minecraft.world.entity.Mob gnome) {
            gnome.snapTo(base.getX() + GX, base.getY(), base.getZ() + 0.5, 180f, 0f);
            gnome.setYHeadRot(180f);
            gnome.setYBodyRot(180f);
            gnome.setNoAi(true);
            level.addFreshEntity(gnome);
        }
        // A person for scale (a plain mannequin, 1.8 blocks): a gnome reaches just past their ankle-to-shin.
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[180f,0f],immovable:1b,hide_description:1b}",
                base.getX() + GX + 1.6, (double) base.getY(), base.getZ() + 0.8));
    }

    private static final List<Step> OTHER_SCENES = List.of(
            new Step("", 40, p -> look(p, 8.5, 0.4, -2.2, 0f, 22f)),
            new Step("ridgeback_egg", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, 8.1, 1.75, -0.7, 8.5, 1.35, 0.5)),
            new Step("ridgeback_egg_close", 10, p -> {}),
            new Step("", 40, p -> look(p, 17.0, 0.7, -1.4, 0f, 6f)),
            new Step("owls", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, 17.0, 1.45, -1.3, 17.0, 1.3, 0.5)),
            new Step("owls_close", 10, p -> {}),
            new Step("", 40, p -> look(p, 28.5, 0.5, -3.0, 0f, -6f)),
            new Step("weasley_clock", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, 38.5, 1.75, -1.7, 38.5, 1.15, 0.5)),
            new Step("ghost", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, 44.5, 1.75, -1.7, 44.5, 1.0, 0.5)),
            new Step("inferius", 10, p -> {}),
            // H3 worldgen, in this void test world (no surface for /place structure to project onto): /place jigsaw
            // from the lane's connector runs the same jigsaw assembly worldgen does, so the Shrieking Shack must
            // attach; /place template lays the street square-on so the camera can stand outside Honeydukes.
            new Step("", 60, p -> command(p.level(), String.format(java.util.Locale.ROOT,
                    "place jigsaw wizards_and_beasts:hogsmeade/high_street wizards_and_beasts:shack_path 3 %d %d %d",
                    LookPreviewCapture.base.getX(), LookPreviewCapture.base.getY() + 1, LookPreviewCapture.base.getZ() - 170))),
            new Step("", 60, p -> eyeAt(p, 0, 95, -170.2, 0, 0, -170)),
            new Step("hogsmeade_worldgen", 10, p -> {}),
            new Step("", 200, p -> command(p.level(), String.format(java.util.Locale.ROOT,
                    "place template wizards_and_beasts:hogsmeade/high_street/a %d %d %d",
                    LookPreviewCapture.base.getX(), LookPreviewCapture.base.getY(), LookPreviewCapture.base.getZ() - 90))),
            new Step("", 60, p -> eyeAt(p, 7.5, 2.6, -70.5, 7.5, 2.4, -78)),
            new Step("hogsmeade_worldgen_street", 10, p -> {}),
            // Inside, behind the windows: the counter and the keeper its post brought (H3 staffing).
            new Step("", 80, p -> eyeAt(p, 7.5, 2.6, -78.6, 7.5, 2.0, -86)),
            new Step("hogsmeade_honeydukes_inside", 10, p -> {}),
            new Step("", 40, p -> eyeAt(p, 19.5, 2.6, -78.6, 19.5, 2.0, -86)),
            new Step("hogsmeade_broomsticks_inside", 10, p -> {}),
            new Step("", 40, p -> {
                p.level().setDayTime(18000);
                look(p, 0.5, 1, 0.5, 180f, -32f);
            }),
            new Step("dark_mark", 10, p -> {}));

    private static BlockPos base = BlockPos.ZERO;
    private static boolean opened;
    private static int stage;
    private static int wait;
    private static int step;
    private static boolean captureThisFrame;
    private static boolean markLit;
    /** Set by a server step, applied on the client tick: the in-hand shots need the hand and hotbar drawn. */
    private static volatile boolean showGui;

    private LookPreviewCapture() {}

    private static void stage(ServerPlayer p) {
        ServerLevel level = p.level();
        // Out in the open: the save has a building at its spawn.
        base = p.blockPosition().offset(40, 0, 40);
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        for (int x = -4; x <= 32; x++) {
            for (int z = -6; z <= 4; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        quidditch(level);
        // Norbert's egg in Hagrid's fire: a brick hearth; the egg model's foot is the item origin, so the display
        // stands on the netherrack.
        for (int x = 6; x <= 10; x++) {
            for (int y = 0; y <= 3; y++) {
                level.setBlockAndUpdate(base.offset(x, y, 1), Blocks.BRICKS.defaultBlockState());
            }
            for (int z = -3; z <= 0; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.BRICKS.defaultBlockState());   // fire can't spread
            }
        }
        // The egg on glowing embers (a magma block), fire burning either side: a campfire's smoke hid it, and a fire
        // block in the same space buried it in flames.
        level.setBlockAndUpdate(base.offset(8, 0, 0), Blocks.MAGMA_BLOCK.defaultBlockState());
        for (int dx : new int[] {-1, 1}) {
            level.setBlockAndUpdate(base.offset(8 + dx, 0, 0), Blocks.NETHERRACK.defaultBlockState());
            level.setBlockAndUpdate(base.offset(8 + dx, 1, 0), Blocks.FIRE.defaultBlockState());
        }
        display(level, base.offset(8, 0, 0), 1.0, "norwegian_ridgeback_egg", 1.0f);
        // The post owls on perches: snowy (Hedwig), great grey (Errol), tawny and barn (the post office).
        String[] coats = {"base", "great_grey", "tawny", "barn"};
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "owl"));
        for (int i = 0; i < coats.length; i++) {
            BlockPos perch = base.offset(14 + i * 2, 0, 0);
            level.setBlockAndUpdate(perch, Blocks.SPRUCE_FENCE.defaultBlockState());
            var owl = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (owl instanceof net.minecraft.world.entity.Mob mob) {
                mob.snapTo(perch.getX() + 0.5, perch.getY() + 1.0, perch.getZ() + 0.5, 180f, 0f);
                mob.setYHeadRot(180f);
                mob.setNoAi(true);
                if (!coats[i].equals("base") && mob instanceof at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity beast) {
                    beast.applyVariant(coats[i]);
                }
                level.addFreshEntity(mob);
            }
        }
        // The Weasley clock on a kitchen wall.
        for (int x = 25; x <= 32; x++) {
            for (int y = 0; y <= 5; y++) {
                level.setBlockAndUpdate(base.offset(x, y, 3), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
        }
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack()
                .withSuppressedOutput().withLevel(level), String.format(java.util.Locale.ROOT,
                "summon minecraft:painting %d %d %d {facing:2b,variant:\"wizards_and_beasts:weasley_clock\"}",
                base.getX() + 28, base.getY() + 2, base.getZ() + 2));
        humanoids(level);
        gnomeGarden(level);
        nifflerFamily(level);
        kappaPond(level);
        qilinMeadow(level);
        zouwuGround(level);
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        markLit = true;
    }

    /** West of the other scenes, so none of them stands in the background. */
    private static final double QX = -60;
    private static final String[] BALLS = {"quaffle", "bludger", "golden_snitch"};
    private static final String[] VIEWS = {"front", "side", "top", "bottom"};
    /** Room per view (the Snitch's wings span two blocks) and the camera's distance back. */
    private static final double[] BALL_SPACING = {1.2, 1.2, 1.8};
    private static final double[] VIEW_BACK = {0.9, 0.8, 1.0};
    private static final double VIEW_Y = 1.6;
    /** Beyond the hoops and far east of the hero shot, so neither shows behind the other. */
    private static final double VIEW_Z = 30;

    private static double viewX(int ball, int view) {
        double start = QX + 60 + 16 * ball;
        return start + view * BALL_SPACING[ball];
    }

    /**
     * The Quidditch balls in vanilla style (owner, 2026-11-25): each one element at model scale 1 (Quaffle 6 px,
     * Bludger 5, Snitch 2 with plane wings) over a pitch with goal hoops, then each ball turned to show its front,
     * side, top and bottom. Every display has a fixed daylight brightness.
     */
    private static void quidditch(ServerLevel level) {
        for (int x = -12; x <= 112; x++) {
            for (int z = -8; z <= 36; z++) {
                level.setBlockAndUpdate(base.offset((int) QX + x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // Three goal hoops behind the hero shot: tall posts and thin gold rings, the middle one tallest.
        int[][] hoops = {{-6, 9}, {2, 12}, {10, 9}};
        for (int[] hoop : hoops) {
            BlockPos foot = base.offset((int) QX + hoop[0], 0, 24);
            for (int y = 0; y < hoop[1]; y++) {
                level.setBlockAndUpdate(foot.above(y), Blocks.OAK_FENCE.defaultBlockState());
            }
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    double r = Math.sqrt(dx * dx + dy * dy);
                    if (r > 2.5 && r < 3.5) {
                        level.setBlockAndUpdate(foot.offset(dx, hoop[1] + 3 + dy, 0), Blocks.GOLD_BLOCK.defaultBlockState());
                    }
                }
            }
        }
        // The hero shot: the balls in a row in the air, Quaffle on the left (+x is the left facing +z).
        ball(level, QX + 2.8, 1.85, 0.5, "quaffle", 1.0, 8, NONE, 0);
        ball(level, QX + 1.9, 1.85, 0.5, "bludger", 1.0, 8, NONE, 0);
        ball(level, QX + 0.9, 2.05, 0.5, "golden_snitch", 1.0, 8, NONE, 0);
        // Four views of each: as made, turned to show its east side, its top, its bottom.
        for (int b = 0; b < BALLS.length; b++) {
            for (int v = 0; v < VIEWS.length; v++) {
                ball(level, viewX(b, v), VIEW_Y, VIEW_Z, BALLS[b], 1.0, 8, VIEW_TURN[v], v);
            }
        }
    }

    private static final float S45 = (float) Math.sqrt(0.5);
    private static final float[] NONE = {0f, 0f, 0f, 1f};
    /** front, side (east face to the camera), top (top to the camera), bottom. */
    private static final float[][] VIEW_TURN = {NONE, {0f, S45, 0f, S45}, {-S45, 0f, 0f, S45}, {S45, 0f, 0f, S45}};

    /**
     * A ball whose centre lands at (x, centreY, z). A geo item's origin sits at the item's centre and the ball's
     * centre {@code centreUnits} above it; the display is turned by quaternion {@code q} about its origin, so the
     * translation moves the turned centre back onto the point (view 0 front/1 side keep it above, 2 top swings it
     * toward -z, 3 bottom toward +z).
     */
    private static void ball(ServerLevel level, double x, double centreY, double z, String item, double scale,
                             int centreUnits, float[] q, int view) {
        double c = centreUnits / 16.0 * scale;
        double tx = 0;
        double ty = view <= 1 ? -c : 0;
        double tz = view == 2 ? c : view == 3 ? -c : 0;
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:item_display %.3f %.3f %.3f {item:{id:\"%s:%s\",count:1},item_display:\"none\","
                        + "brightness:{sky:15,block:0},"
                        + "transformation:{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]}}",
                base.getX() + x, base.getY() + centreY, base.getZ() + z, WizardsAndBeastsMod.MODID, item,
                q[0], q[1], q[2], q[3], tx, ty, tz, scale, scale, scale));
    }

    private static void command(ServerLevel level, String cmd) {
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level), cmd);
    }

    /** Selects a hotbar slot on the server and tells the client, which owns the selected slot. */
    private static void hold(ServerPlayer p, int slot) {
        p.getInventory().setSelectedSlot(slot);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(slot));
    }

    /** Places the eye at (ex, ey, ez) looking at (tx, ty, tz), all base-relative. */
    private static void eyeAt(ServerPlayer p, double ex, double ey, double ez, double tx, double ty, double tz) {
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        eye(p, ex, ey, ez, yaw, pitch);
    }

    /** Places the spectator so the eye, 1.62 above the feet, is at these base-relative coordinates. */
    private static void eye(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        look(p, x, y - 1.62, z, yaw, pitch);
    }

    /**
     * The humanoid look sheets. Nearly Headless Nick is the ghost creature (translucent through its tint) in a stone
     * corridor; the Inferius stands on a mannequin, vanilla's player-shaped stand that wears any skin -- the way
     * vanilla makes its husk and drowned from the zombie -- in the cave lake's water.
     */
    private static void humanoids(ServerLevel level) {
        for (int x = 34; x <= 48; x++) {
            for (int z = -4; z <= 2; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
            for (int y = 0; y <= 4; y++) {
                level.setBlockAndUpdate(base.offset(x, y, 2), (x + y) % 5 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = 42; x <= 47; x++) {
            for (int z = -2; z <= 1; z++) {
                // A pool sunk into the floor, so the water has nowhere to run.
                level.setBlockAndUpdate(base.offset(x, -2, z), Blocks.DEEPSLATE.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.WATER.defaultBlockState());
            }
        }
        // The ghost is the creature itself: its tint's alpha is what makes it translucent. Set here because a
        // creature without AI may not tick its abilities.
        var ghostType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ghost"));
        if (ghostType.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                instanceof at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity ghost) {
            ghost.snapTo(base.getX() + 38.5, base.getY() + 0.15, base.getZ() + 0.5, 180f, 0f);
            ghost.setYHeadRot(180f);
            ghost.setNoAi(true);
            ghost.setNoGravity(true);
            ghost.setTint(0xB8E6F0FF);
            level.addFreshEntity(ghost);
        }
        mannequin(level, 44.5, -0.7, 0.5, "inferius");     // rising out of the lake
    }

    private static void mannequin(ServerLevel level, double x, double y, double z, String skin) {
        command(level, String.format(java.util.Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {profile:{texture:\"%s:entity/humanoid/%s\"},"
                        + "Rotation:[180f,0f],immovable:1b,hide_description:1b}",
                base.getX() + x, base.getY() + y, base.getZ() + z, WizardsAndBeastsMod.MODID, skin));
    }

    private static void display(ServerLevel level, BlockPos at, double up, String item, float scale) {
        String cmd = String.format(java.util.Locale.ROOT,
                "summon minecraft:item_display %.2f %.2f %.2f {item:{id:\"%s:%s\",count:1},item_display:\"none\","
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
                at.getX() + 0.5, at.getY() + up, at.getZ() + 0.5, WizardsAndBeastsMod.MODID, item, scale, scale, scale);
        level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level), cmd);
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), base.getX() + x, base.getY() + y, base.getZ() + z, Set.of(), yaw, pitch, false);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1280, 720);
        stage = 1;
        wait = 80;
        mc.createWorldOpenFlows().openWorld(
                System.getenv().getOrDefault("WB_LOOK_PREVIEW_WORLD", "wb_entity_showcase"), () -> stage = 3);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == 0 || stage == 3) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            return;
        }
        if (mc.options.hideGui == showGui) {
            mc.options.hideGui = !showGui;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        if (markLit && mc.player.tickCount % 5 == 0) {
            server.execute(() -> {
                ServerLevel level = server.overworld();
                at.koopro.wizardsandbeasts.spell.darkmark.DarkMark.draw(level,
                        Vec3.atCenterOf(base).add(0, 44, -56), 1.6, 0f);
            });
        }
        if (captureThisFrame || --wait > 0) {
            return;
        }
        if (stage == 1) {
            stage = 2;
            step = 0;
            begin(mc, server);
            return;
        }
        if (!SCRIPT.get(step).shot().isEmpty()) {
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
        Path file = mc.gameDirectory.toPath().resolve(Path.of(Screenshot.SCREENSHOT_DIR, "look_preview"))
                .resolve(SCRIPT.get(step).shot() + ".png");
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(file.getParent());
                image.writeToFile(file);
            } catch (IOException e) {
                LOGGER.error("Look preview: cannot write {}", file, e);
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
            stage = 3;
            LOGGER.info("Look preview: done");
            mc.stop();
            return;
        }
        begin(mc, server);
    }

    private static void begin(Minecraft mc, MinecraftServer server) {
        Step current = SCRIPT.get(step);
        wait = current.ticks();
        if (!current.shot().isEmpty()) {
            return;
        }
        mc.setScreen(null);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                current.server().accept(player);
            }
        });
    }

    /** Last, so every static it reads is already set (static fields initialise in textual order). */
    private static final List<Step> SCRIPT = script();
}
